/*
    BeepBeep, an event stream processor
    Copyright (C) 2008-2018 Sylvain Hallé

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
package ca.uqac.lif.cep.functions;

import ca.uqac.lif.cep.Processor;
import ca.uqac.lif.cep.Pullable;
import ca.uqac.lif.cep.Pushable;

import java.util.Iterator;
import java.util.concurrent.Future;

/**
 * Evaluates a function on each event front in a <em>lazy</em> manner.
 * 
 * @author Sylvain Hallé
 */
public class ApplyFunctionLazy extends Processor 
{
  /**
   * The function to apply on input events
   */
  protected Function m_function;

  /**
   * An array of input pushables
   */
  protected final transient Pushable[] m_inputPushables;

  /**
   * An array of output pullables
   */
  protected transient Pullable[] m_outputPullables;

  /**
   * An array containing the number of the front corresponding to
   * the head of the input queue for each pipe
   */
  protected int[] m_frontNumber;

  /**
   * The number of input fronts that have been processed by
   * the processor
   */
  protected int m_numCurrentFront;

  /**
   * An array containing the events received so far in the current
   * input front
   */
  protected Object[] m_inputFront;

  /**
   * Creates a new lazy application processor.
   * @param f The function to apply on each input event
   */
  public ApplyFunctionLazy(/*@ non_null @*/ Function f)
  {
    super(f.getInputArity(), f.getOutputArity());
    m_function = f;
    m_inputPushables = new Pushable[f.getInputArity()];
    m_outputPullables = new Pullable[f.getOutputArity()];
    m_frontNumber = new int[f.getInputArity()];
    m_inputFront = new Object[f.getInputArity()];
    m_numCurrentFront = 0;
  }

  @Override
  public synchronized Pushable getPushableInput(int index)
  {
    if (m_inputPushables[index] == null)
    {
      m_inputPushables[index] = new InputPushable(index);
    }
    return m_inputPushables[index];
  }

  @Override
  public synchronized Pullable getPullableOutput(int index)
  {
    if (m_outputPullables[index] == null)
    {
      m_outputPullables[index] = new OutputPullable(index);
    }
    return m_outputPullables[index];
  }

  @Override
  public ApplyFunctionLazy duplicate(boolean with_state)
  {
    return new ApplyFunctionLazy(m_function.duplicate(with_state));
  }

  protected class InputPushable implements Pushable
  {
    /**
     * The index of the input pipe this pushable is connected to
     */
    protected int m_index;

    /**
     * The maximum number of loops that a call to push can perform.
     * This is just a safety to avoid infinite loops.
     */
    protected static final int MAX_LOOPS = 10000000;

    public InputPushable(int index)
    {
      super();
      m_index = index;
    }

    @Override
    public Pushable push(Object o)
    {
      Object[] out_front = new Object[m_outputPullables.length];
      if (m_frontNumber[m_index] < m_numCurrentFront)
      {
        // This is an event from a front that has already
        // been processed. Don't bother putting it in the
        // queue or evaluate the function.
        m_frontNumber[m_index]++;
        return this;
      }
      assert m_frontNumber[m_index] >= m_numCurrentFront;
      m_inputQueues[m_index].add(o);
      boolean evaluated = true;
      // the following is actually a bounded while(evaluated)
      for (int loop_count = 0; loop_count < MAX_LOOPS && evaluated; loop_count++)
      {
        for (int i = 0; i < m_inputPullables.length; i++)
        {
          if (m_frontNumber[i] == m_numCurrentFront)
          {
            if (!m_inputQueues[i].isEmpty())
            {
              m_inputFront[i] = m_inputQueues[i].remove();
              m_frontNumber[i]++;
            }
            else
            {
              m_inputFront[i] = null;
            }
          }
        }
        evaluated = m_function.evaluateLazy(m_inputFront, out_front, m_context);
        if (evaluated)
        {
          for (int i = 0; i < out_front.length; i++)
          {
            m_outputPushables[i].push(out_front[i]);
          }
          for (int i = 0; i < m_inputFront.length; i++)
          {
            m_inputFront[i] = null;
          }
          m_numCurrentFront++;
        }
      }
      return this;
    }

    @Override
    public Future<Pushable> pushFast(Object o)
    {
      push(o);
      return Pushable.NULL_FUTURE;
    }

    @Override
    public void notifyEndOfTrace() throws PushableException
    {
      // Nothing to do
    }

    @Override
    public Processor getProcessor()
    {
      return ApplyFunctionLazy.this;
    }

    @Override
    public int getPosition()
    {
      return m_index;
    }
  }

  protected class OutputPullable implements Pullable
  {
    /**
     * The index of the input pipe this pullable is connected to
     */
    protected int m_index;

    public OutputPullable(int index)
    {
      super();
      m_index = index;
    }

    @Override
    public Iterator<Object> iterator()
    {
      return this;
    }

    @Override
    public Object pullSoft()
    {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public Object pull()
    {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public Object next()
    {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public NextStatus hasNextSoft() 
    {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public boolean hasNext() 
    {
      // TODO Auto-generated method stub
      return false;
    }

    @Override
    public Processor getProcessor() 
    {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public int getPosition() 
    {
      // TODO Auto-generated method stub
      return 0;
    }

    @Override
    public void start() 
    {
      // TODO Auto-generated method stub

    }

    @Override
    public void stop() 
    {
      // TODO Auto-generated method stub

    }

    @Override
    public void dispose()
    {
      // TODO Auto-generated method stub

    }
  }
}