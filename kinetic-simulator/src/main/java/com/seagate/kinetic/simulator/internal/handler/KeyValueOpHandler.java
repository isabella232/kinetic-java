/**
 * 
 * Copyright (C) 2014 Seagate Technology.
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */
package com.seagate.kinetic.simulator.internal.handler;

import com.seagate.kinetic.common.lib.KineticMessage;
import com.seagate.kinetic.simulator.persist.KVOp;

public class KeyValueOpHandler extends CommandHandlerBase implements
        CommandHandler {

    @SuppressWarnings("unchecked")
    @Override
    public void processRequest(KineticMessage request, KineticMessage response)
            throws ServiceException {

        KVOp.Op(engine.getAclMap(), engine.getStore(), request, response);
    }

}