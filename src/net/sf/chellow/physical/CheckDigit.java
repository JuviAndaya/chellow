/*
 
 Copyright 2005 Meniscus Systems Ltd
 
 This file is part of Chellow.

 Chellow is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2 of the License, or
 (at your option) any later version.

 Chellow is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Chellow; if not, write to the Free Software
 Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

 */

package net.sf.chellow.physical;




import org.w3c.dom.Attr;
import org.w3c.dom.Document;

import net.sf.chellow.monad.InternalException;
import net.sf.chellow.monad.HttpException;
import net.sf.chellow.monad.types.MonadCharacter;


public class CheckDigit extends MonadCharacter {
	private static final long serialVersionUID = -5301693222758775023L;

	public CheckDigit() {
		setDigitOnly(true);
	}

	public CheckDigit(Character character) throws HttpException, InternalException {
		this(null, character);
	}

	public CheckDigit(String label, Character character) throws HttpException, InternalException
			{
		this();
		setLabel(label);
			update(character);
	}
	
	public Attr toXml(Document doc) {
		Attr attribute = doc.createAttribute("check-digit");
		attribute.setNodeValue(toString());
		return attribute;
	}
}