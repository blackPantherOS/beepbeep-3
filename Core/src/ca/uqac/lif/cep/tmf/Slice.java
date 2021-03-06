/*
    BeepBeep, an event stream processor
    Copyright (C) 2008-2016 Sylvain Hallé

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ca.uqac.lif.cep.tmf;

import ca.uqac.lif.cep.Connector;
import ca.uqac.lif.cep.Processor;
import ca.uqac.lif.cep.ProcessorException;
import ca.uqac.lif.cep.Pushable;
import ca.uqac.lif.cep.UniformProcessor;
import ca.uqac.lif.cep.functions.Function;
import ca.uqac.lif.cep.functions.FunctionException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Separates an input trace into different "slices". The slicer takes as input a
 * processor <i>p</i> and a slicing function <i>f</i>. There exists potentially
 * one instance of <i>p</i> for each value in the image of <i>f</i>.
 * <p>
 * When an event <i>e</i> arrives, the slicer evaluates <i>c</i> =
 * <i>f</i>(<i>e</i>). This value determines to what instance of <i>p</i> the
 * event will be dispatched. If no slice with such value currently exists, a new
 * instance of <i>p</i> will be created. The processor then outputs a
 * <code>Collection</code> object (typically a set) containing the <em>last</em>
 * events returned by every slice.
 * 
 * <p>
 * The function <i>f</i> may return <code>null</code>, or the special object
 * {@link ToAllSlices}. This indicates that no new slice must be created, but
 * that the incoming event must be dispatched to <em>all</em> slices one by one.
 * 
 * @author Sylvain Hallé
 * @since 0.3
 */
@SuppressWarnings("squid:S2160")
public class Slice extends UniformProcessor
{
  /**
   * The slicing function
   */
  protected Function m_slicingFunction = null;

  /**
   * The internal processor
   */
  protected Processor m_processor = null;

  /**
   * The cleaning function
   */
  protected Function m_cleaningFunction = null;

  protected HashMap<Object, Processor> m_slices;

  protected HashMap<Object, QueueSink> m_sinks;

  /**
   * The last value output by the processor for each slice
   */
  protected HashMap<Object, Object> m_lastValues;

  /**
   * If the slicing function returns a collection, treat each element of the
   * collection as a slice id.
   */
  protected boolean m_explodeArrays = false;

  protected Slice()
  {
    super(1, 1);
  }

  /**
   * Creates a new Slice processor.
   * 
   * @param func
   *          The slicing function
   * @param proc
   *          The processor to apply on each slice
   * @param clean_func
   *          The cleaning function
   */
  public Slice(/* @ non_null @ */ Function func, /* @ non_null @ */ Processor proc,
      Function clean_func)
  {
    super(proc.getInputArity(), proc.getOutputArity());
    m_processor = proc;
    m_slicingFunction = func;
    m_cleaningFunction = clean_func;
    m_slices = new HashMap<Object, Processor>();
    m_sinks = new HashMap<Object, QueueSink>();
    m_lastValues = new HashMap<Object, Object>();
  }

  public Slice(/* @NonNull */ Function func, /* @NonNull */ Processor proc)
  {
    this(func, proc, null);
  }

  /**
   * Sets the processor that will be executed on each slice.
   * 
   * @param p
   *          The processor
   * @return This slicer
   */
  public Slice setProcessor(Processor p)
  {
    m_processor = p;
    return this;
  }

  @Override
  protected boolean compute(Object[] inputs, Object[] outputs)
  {
    int output_arity = getOutputArity();
    Object[] f_value = new Object[1];
    try
    {
      m_slicingFunction.evaluate(inputs, f_value);
    }
    catch (FunctionException e)
    {
      throw new ProcessorException(e);
    }
    Object slice_ids = f_value[0];
    if (slice_ids == null)
    {
      // This event applies to no slice; don't bother processing it
      outputs[0] = m_lastValues;
      return true;
    }
    Object[] slice_vals;
    if (m_explodeArrays)
    {
      if (slice_ids.getClass().isArray())
      {
        slice_vals = (Object[]) slice_ids;
      }
      if (slice_ids instanceof Collection)
      {
        Collection<?> col = (Collection<?>) slice_ids;
        slice_vals = new Object[col.size()];
        int i = 0;
        for (Object o : col)
        {
          slice_vals[i] = o;
          i++;
        }
      }
      else
      {
        slice_vals = new Object[] { slice_ids };
      }
    }
    else
    {
      slice_vals = new Object[] { slice_ids };
    }
    for (Object slice_id : slice_vals)
    {
      Set<Object> slices_to_process = new HashSet<Object>();
      if (slice_id instanceof ToAllSlices || slice_id == null)
      {
        slices_to_process.addAll(m_slices.keySet());
      }
      else
      {
        if (!m_slices.containsKey(slice_id))
        {
          // First time we see this value: create new slice
          Processor p = m_processor.duplicate();
          m_slices.put(slice_id, p);
          addContextFromSlice(p, slice_id);
          QueueSink sink = new QueueSink(output_arity);
          Connector.connect(p, sink);
          m_sinks.put(slice_id, sink);
          // Put dummy value temporarily
          m_lastValues.put(slice_id, null);
        }
        slices_to_process.add(slice_id);
      }
      @SuppressWarnings("unchecked")
      Future<Pushable>[] futures = new Future[slices_to_process.size()];
      for (Object s_id : slices_to_process)
      {
        // Find processor corresponding to that slice
        Processor slice_p = m_slices.get(s_id);
        // If this slice hasn't been cleaned up...
        if (slice_p != null)
        {
          QueueSink sink_p = m_sinks.get(s_id);
          // Push the input into the processor
          // Pushable[] p_array = new Pushable[inputs.length];
          for (int i = 0; i < inputs.length; i++)
          {
            Object o_i = inputs[i];
            Pushable p = slice_p.getPushableInput(i);
            Future<Pushable> f = p.pushFast(o_i);
            if (f == null)
            {
              System.out.println("NULL");
            }
            futures[i] = f;
            // p_array[i] = p;
          }
          // Wait for all slices to be done
          for (Future<Pushable> f : futures)
          {
            if (f != null)
            {
              try
              {
                f.get();
              }
              catch (InterruptedException e)
              {
                throw new ProcessorException(e);
              }
              catch (ExecutionException e)
              {
                throw new ProcessorException(e);
              }
            }
          }
          // Collect the output from that processor
          Object[] out = sink_p.remove();
          // Can we clean that slice?
          Object[] can_clean = new Object[1];
          if (m_cleaningFunction != null)
          {
            try
            {
              m_cleaningFunction.evaluate(out, can_clean);
            }
            catch (FunctionException e)
            {
              throw new ProcessorException(e);
            }
          }
          if (can_clean != null && can_clean.length > 0 && can_clean[0] instanceof Boolean
              && (Boolean) (can_clean[0]))
          {
            // Yes: remove the processor for that slice
            m_slices.remove(s_id);
            m_sinks.remove(s_id);
          }
          m_lastValues.put(s_id, out[0]);
        }
      }
      outputs[0] = m_lastValues;
    }
    return true;
  }

  /**
   * Sets whether a slice function that returns a collection of values must be
   * handled as individual slice IDs.
   * 
   * @param b
   *          Set to <tt>true</tt> to handle collections as multiple IDs. The
   *          default is <tt>false</tt>
   * @return This slicer
   */
  public Slice explodeCollections(boolean b)
  {
    m_explodeArrays = b;
    return this;
  }

  /**
   * Adds elements to the context of a newly created slice. By default, nothing is
   * done. Descendants of this class may want to add context elements to the
   * processor, based on the value of the newly created slice.
   * 
   * @param p
   *          The newly created processor
   * @param slice
   *          The value associated to the slice
   */
  public void addContextFromSlice(Processor p, Object slice)
  {
    // By default, do nothing
  }

  @Override
  public void reset()
  {
    super.reset();
    m_slices.clear();
    m_slicingFunction.reset();
    if (m_cleaningFunction != null)
    {
      m_cleaningFunction.reset();
    }
  }

  /**
   * Gets the number of slices the slicer currently handles
   * 
   * @return The number of slices
   */
  public int getActiveSliceCount()
  {
    return m_slices.size();
  }

  @Override
  public Slice duplicate(boolean with_state)
  {
    Slice s = null;
    if (m_cleaningFunction == null)
    {
      s = new Slice(m_slicingFunction.duplicate(with_state), m_processor.duplicate(with_state));
    }
    else
    {
      s = new Slice(m_slicingFunction.duplicate(with_state), m_processor.duplicate(with_state),
          m_cleaningFunction.duplicate(with_state));
    }
    s.setContext(m_context);
    s.m_explodeArrays = m_explodeArrays;
    if (with_state)
    {
      throw new UnsupportedOperationException(
          "Duplication with state not supported yet on this processor");
    }
    return s;
  }

  /**
   * Dummy object telling the slicer that an event must be sent to all slices
   */
  public static class ToAllSlices
  {
    public static final ToAllSlices instance = new ToAllSlices();

    private ToAllSlices()
    {
      super();
    }
  }
}
