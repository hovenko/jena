/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hp.hpl.jena.sdb.layout1;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.sparql.util.FmtUtils;

import com.hp.hpl.jena.sdb.compiler.QuadBlock;
import com.hp.hpl.jena.sdb.compiler.SlotCompiler;
import com.hp.hpl.jena.sdb.core.SDBRequest;
import com.hp.hpl.jena.sdb.core.sqlexpr.*;
import com.hp.hpl.jena.sdb.core.sqlnode.SqlNode;

public class SlotCompiler1 extends SlotCompiler
{
    private EncoderDecoder codec ;

    public SlotCompiler1(SDBRequest request, EncoderDecoder codec)
    {
        super(request) ;
        this.codec = codec ;
    }
    
    @Override
    public SqlNode start(QuadBlock quads)
    { return null ; }

    @Override
    public SqlNode finish(SqlNode sqlNode, QuadBlock quads)
    { return sqlNode ; }
    
    @Override
    protected void constantSlot(SDBRequest request, Node node, SqlColumn thisCol, SqlExprList conditions)
    {
          String str = codec.encode(node) ;
          SqlExpr c = new S_Equal(thisCol, new SqlConstant(str)) ;
          c.addNote("Const: "+FmtUtils.stringForNode(node)) ;
          conditions.add(c) ;
          return ;
    }
}
