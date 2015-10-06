/*
    BeepBeep, an event stream processor
    Copyright (C) 2008-2015 Sylvain Hallé

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ca.uqac.lif.cep.ltl;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Stack;

import ca.uqac.lif.cep.Connector;
import ca.uqac.lif.cep.Processor;
import ca.uqac.lif.cep.SingleProcessor;
import ca.uqac.lif.cep.eml.tuples.EmlBoolean;

public abstract class UnaryProcessor extends SingleProcessor 
{
	public UnaryProcessor()
	{
		super(1, 1);
	}

	@Override
	protected final Queue<Object[]> compute(Object[] inputs) 
	{
		Object o = inputs[0];
		if (o == null)
		{
			return new ArrayDeque<Object[]>();
		}
		EmlBoolean result = compute(EmlBoolean.toEmlBoolean(o));
		if (result == null)
		{
			return null;
		}
		Object[] out = new Object[1];
		out[0] = result;
		return wrapVector(out);
	}
	
	protected abstract EmlBoolean compute(EmlBoolean input);

	@Override
	public void build(Stack<Object> stack) 
	{
		stack.pop(); // (
		Processor p = (Processor) stack.pop();
		stack.pop(); // )
		stack.pop(); // G
		Connector.connect(p,  this);
		stack.push(this);
	}
}