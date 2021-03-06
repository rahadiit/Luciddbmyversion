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
package net.sf.farrago.query;

import java.util.*;

import net.sf.farrago.catalog.*;
import net.sf.farrago.cwm.behavioral.*;
import net.sf.farrago.cwm.relational.enumerations.*;
import net.sf.farrago.fem.sql2003.*;
import net.sf.farrago.session.*;
import net.sf.farrago.type.*;

import org.eigenbase.reltype.*;
import org.eigenbase.sql.*;
import org.eigenbase.sql.type.*;
import org.eigenbase.util.*;


/**
 * FarragoUserDefinedRoutineLookup implements the {@link SqlOperatorTable}
 * interface by looking up user-defined functions from the repository.
 *
 * @author John V. Sichi
 * @version $Id$
 */
public class FarragoUserDefinedRoutineLookup
    implements SqlOperatorTable
{
    //~ Instance fields --------------------------------------------------------

    private final FarragoSessionStmtValidator stmtValidator;

    private final FarragoPreparingStmt preparingStmt;

    private final FemRoutine validatingRoutine;

    //~ Constructors -----------------------------------------------------------

    public FarragoUserDefinedRoutineLookup(
        FarragoSessionStmtValidator stmtValidator,
        FarragoPreparingStmt preparingStmt,
        FemRoutine validatingRoutine)
    {
        this.stmtValidator = stmtValidator;
        this.preparingStmt = preparingStmt;
        this.validatingRoutine = validatingRoutine;
    }

    //~ Methods ----------------------------------------------------------------

    // implement SqlOperatorTable
    public List<SqlOperator> lookupOperatorOverloads(
        SqlIdentifier opName,
        SqlFunctionCategory category,
        SqlSyntax syntax)
    {
        if ((preparingStmt != null) && preparingStmt.isExpandingDefinition()) {
            // While expanding view and function bodies, an unqualified name is
            // assumed to be a builtin, because we explicitly qualify everything
            // else when the definition is stored.  We could qualify the
            // builtins with INFORMATION_SCHEMA, but we currently don't.
            if (opName.names.length == 1) {
                return Collections.emptyList();
            }
        }
        if (category == SqlFunctionCategory.UserDefinedSpecificFunction) {
            // Look up by specific name instead of invocation name.
            FemRoutine femRoutine =
                stmtValidator.findSchemaObject(opName, FemRoutine.class);
            List<SqlOperator> overloads = new ArrayList<SqlOperator>();
            if (femRoutine.getType() == ProcedureTypeEnum.FUNCTION) {
                overloads.add(convertRoutine(femRoutine));
            }
            return overloads;
        }

        if (syntax != SqlSyntax.Function) {
            return Collections.emptyList();
        }

        List<FemRoutine> list =
            stmtValidator.findRoutineOverloads(
                opName,
                null);
        List<SqlOperator> overloads = new ArrayList<SqlOperator>();
        for (FemRoutine femRoutine : list) {
            if (category == SqlFunctionCategory.UserDefinedFunction) {
                if (femRoutine.getType() != ProcedureTypeEnum.FUNCTION) {
                    continue;
                }
            } else if (category == SqlFunctionCategory.UserDefinedProcedure) {
                if (femRoutine.getType() != ProcedureTypeEnum.PROCEDURE) {
                    continue;
                }
            }
            if (femRoutine.getVisibility() == null) {
                // Oops, the referenced routine hasn't been validated yet.  If
                // requested, throw a special exception and someone up
                // above will figure out what to do.
                if (validatingRoutine == null) {
                    throw new FarragoUnvalidatedDependencyException();
                }
                if (!femRoutine.equals(validatingRoutine)) {
                    // just skip this one for now; if there's a conflict,
                    // we'll hit it by symmetry
                    continue;
                }
            }
            SqlFunction sqlFunction = convertRoutine(femRoutine);
            overloads.add(sqlFunction);
        }
        return overloads;
    }

    // implement SqlOperatorTable
    public List<SqlOperator> getOperatorList()
    {
        // NOTE jvs 1-Jan-2005:  I don't think we'll ever need this.
        throw Util.needToImplement(this);
    }

    /**
     * Converts the validated catalog definition of a routine into SqlFunction
     * representation.
     *
     * @param femRoutine catalog definition
     *
     * @return converted function
     */
    public FarragoUserDefinedRoutine convertRoutine(FemRoutine femRoutine)
    {
        int nParams = FarragoCatalogUtil.getRoutineParamCount(femRoutine);
        FarragoTypeFactory typeFactory = stmtValidator.getTypeFactory();

        RelDataType [] paramTypes = new RelDataType[nParams];
        Iterator paramIter = femRoutine.getParameter().iterator();
        RelDataType returnType = null;

        if (FarragoCatalogUtil.isTableFunction(femRoutine)) {
            // pretend that table function returns a ROW type,
            // even though technically it returns a MULTISET of ROW
            returnType = typeFactory.createStructTypeFromClassifier(femRoutine);
        }

        for (int i = 0; paramIter.hasNext(); ++i) {
            FemRoutineParameter param = (FemRoutineParameter) paramIter.next();

            // REVIEW jvs 8-Jan-2006:  this is here to avoid problems
            // with the bogus return type on table functions; get
            // rid of it once we clean that up
            if (param.getKind() == ParameterDirectionKindEnum.PDK_RETURN) {
                if (returnType != null) {
                    continue;
                }
            }

            RelDataType type = typeFactory.createCwmElementType(param);

            if (param.getKind() == ParameterDirectionKindEnum.PDK_RETURN) {
                returnType = type;
            } else {
                paramTypes[i] = type;
            }
        }

        if (returnType == null) {
            // for procedures without dynamic result sets, we make up a dummy
            // return type to allow invocations to be rewritten as functions.
            // Use a DML-compatible return type so that invocation can look
            // like DML.
            returnType = typeFactory.createSqlType(SqlTypeName.BIGINT);
        }

        if (FarragoCatalogUtil.isRoutineConstructor(femRoutine)) {
            // constructors always return NOT NULL
            returnType =
                typeFactory.createTypeWithNullability(
                    returnType,
                    false);
        }

        return new FarragoUserDefinedRoutine(
            stmtValidator,
            preparingStmt,
            femRoutine,
            returnType,
            paramTypes);
    }
}

// End FarragoUserDefinedRoutineLookup.java
