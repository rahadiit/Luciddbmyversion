/*
// Licensed to DynamoBI Corporation (DynamoBI) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  DynamoBI licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at

//   http://www.apache.org/licenses/LICENSE-2.0

// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
*/
package org.eigenbase.applib.variable;

import net.sf.farrago.catalog.*;
import net.sf.farrago.cwm.core.*;
import net.sf.farrago.cwm.instance.*;

import org.eigenbase.applib.resource.*;


/**
 * SQL-invocable procedure to set the value of an application variable.
 *
 * @author John V. Sichi
 * @version $Id$
 */
public abstract class SetAppVarUdp
{
    //~ Methods ----------------------------------------------------------------

    public static void execute(String contextId, String varId, String newValue)
    {
        if (varId == null) {
            throw ApplibResource.instance().AppVarIdRequired.ex();
        }
        FarragoRepos repos = null;
        FarragoReposTxnContext txn = null;
        try {
            repos = AppVarUtil.getRepos();
            txn = repos.newTxnContext(true);
            txn.beginWriteTxn();
            CwmExtent context = AppVarUtil.lookupContext(repos, contextId);
            repos.setTagValue(
                context,
                varId,
                (newValue == null) ? AppVarUtil.NULL_APPVAR_VALUE : newValue);
            txn.commit();
        } catch (Throwable ex) {
            throw ApplibResource.instance().AppVarWriteFailed.ex(
                contextId,
                varId,
                ex);
        } finally {
            if (txn != null) {
                txn.rollback();
            }
        }
    }
}

// End SetAppVarUdp.java
