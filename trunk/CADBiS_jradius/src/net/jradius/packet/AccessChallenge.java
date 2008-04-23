/**
 * JRadius - A RADIUS Server Java Adapter
 * Copyright (C) 2004-2005 PicoPoint, B.V.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

package net.jradius.packet;

/**
 *  The RADIUS Access-Challenge Packet.
 *  See http://www.iana.org/assignments/radius-types
 *
 * @author David Bird
 */
public class AccessChallenge extends RadiusResponse
{
    public static final byte CODE = (byte)11;
    private static final long serialVersionUID = (long)CODE;
    
    public AccessChallenge() 
    {
        code = CODE;
    }
}
