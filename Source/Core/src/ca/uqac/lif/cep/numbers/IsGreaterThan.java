/*
    BeepBeep, an event stream processor
    Copyright (C) 2008-2015 Sylvain Hallé

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
package ca.uqac.lif.cep.numbers;

import java.util.Stack;

import ca.uqac.lif.cep.BinaryFunction;

public class IsGreaterThan extends BinaryFunction<Number,Number,Boolean>
{
	/**
	 * A static instance of the function
	 */
	public static final transient IsGreaterThan instance = new IsGreaterThan();
	
	private IsGreaterThan()
	{
		super();
	}
	
	public static void build(Stack<Object> stack)
	{
		stack.pop();
		stack.push(new IsGreaterThan());
	}

	@Override
	public Boolean evaluate(Number x, Number y) 
	{
		return x.floatValue() > y.floatValue();
	}

	@Override
	public Boolean getStartValue() 
	{
		return false;
	}
}