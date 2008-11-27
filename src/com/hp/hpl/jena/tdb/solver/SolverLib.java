/*
 * (c) Copyright 2008 Hewlett-Packard Development Company, LP
 * All rights reserved.
 * [See end of file]
 */

package com.hp.hpl.jena.tdb.solver;

import static com.hp.hpl.jena.tdb.lib.Lib.printAbbrev;
import iterator.Iter;
import iterator.Transform;

import java.util.Iterator;
import java.util.List;

import lib.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.sparql.core.BasicPattern;
import com.hp.hpl.jena.sparql.core.Var;
import com.hp.hpl.jena.sparql.engine.ExecutionContext;
import com.hp.hpl.jena.sparql.engine.QueryIterator;
import com.hp.hpl.jena.sparql.engine.binding.Binding;
import com.hp.hpl.jena.sparql.engine.binding.BindingMap;
import com.hp.hpl.jena.tdb.lib.NodeLib;
import com.hp.hpl.jena.tdb.store.DatasetGraphTDB;
import com.hp.hpl.jena.tdb.store.GraphTDB;
import com.hp.hpl.jena.tdb.store.NodeId;
import com.hp.hpl.jena.tdb.store.NodeTable;
import com.hp.hpl.jena.tdb.store.NodeTupleTable;

/** Utilities used within the TDB BGP solver */
public class SolverLib
{
    private static Logger log = LoggerFactory.getLogger(SolverLib.class) ; 
    
    public interface ConvertNodeIDToNode { 
        public Iterator<Binding> convert(NodeTable nodeTable, Iterator<BindingNodeId> iterBindingIds) ;
    }
    
    /** Change this to change the process of NodeId to Node conversion. 
     * Normally it's this code, which puts a delayed iterator mapping
     * around the BindingNodeId stream. 
     */
    public static ConvertNodeIDToNode converter = new ConvertNodeIDToNode(){
        @Override
        public Iterator<Binding> convert(NodeTable nodeTable, Iterator<BindingNodeId> iterBindingIds)
        {
            return Iter.map(iterBindingIds, convToBinding(nodeTable)) ;
        }} ;

        /** Non-reordering execution of a basic graph pattern, given a iterator of bindings as input */ 
    public static QueryIterator execute(GraphTDB graph, BasicPattern pattern, QueryIterator input, ExecutionContext execCxt)
    {
        List<Triple> triples = NodeLib.tripleList(pattern) ;
        @SuppressWarnings("unchecked") Iterator<Binding> iter = (Iterator<Binding>)input ;
        
        NodeTable nodeTable = graph.getNodeTupleTable().getNodeTable() ;
        Iterator<BindingNodeId> chain = Iter.map(iter, SolverLib.convFromBinding(nodeTable)) ;
        
        for ( Triple triple : triples )
        {
            Tuple<Node> t = graph.asTuple(triple) ; 
            chain = solve(graph.getNodeTupleTable(), chain, t, execCxt) ;
        }
        
        Iterator<Binding> iterBinding = converter.convert(nodeTable, chain) ;
        return new QueryIterTDB(iterBinding, input, execCxt) ;
    }
    
    // Abstract: NodeTupleTable, Tuple generator, input, execCxt 
    /** Non-reordering execution of a quad pattern, given a iterator of bindings as input */ 
    public static QueryIterator execute(DatasetGraphTDB ds, Node graphNode, BasicPattern pattern, QueryIterator input, ExecutionContext execCxt)
    {
        List<Triple> triples = NodeLib.tripleList(pattern) ;
        @SuppressWarnings("unchecked") Iterator<Binding> iter = (Iterator<Binding>)input ;
        NodeTable nodeTable = ds.getQuadTable().getNodeTable() ;
        Iterator<BindingNodeId> chain = Iter.map(iter, SolverLib.convFromBinding(nodeTable)) ;
        
        for ( Triple triple : triples )
        {
            Tuple<Node> tuple = new Tuple<Node>(graphNode, triple.getSubject(), triple.getPredicate(), triple.getObject()) ;
            chain = solve(ds.getQuadTable(), chain, tuple, execCxt) ;
        }
        
        Iterator<Binding> iterBinding = converter.convert(nodeTable, chain) ;
        return new QueryIterTDB(iterBinding, input, execCxt) ;
    }
    
    private static Iterator<BindingNodeId> solve(NodeTupleTable nodeTupleTable, Iterator<BindingNodeId> chain, 
                                                 Tuple<Node> tuple, ExecutionContext execCxt)
    {
        return new StageMatchTriple(nodeTupleTable, chain, tuple, execCxt) ;
    }
    
    // Transform : BindingNodeId ==> Binding
    private static Transform<BindingNodeId, Binding> convToBinding(final NodeTable nodeTable)
    {
        return new Transform<BindingNodeId, Binding>()
        {
            @Override
            public Binding convert(BindingNodeId bindingNodeIds)
            {
                if ( true )
                    return new BindingTDB(null, bindingNodeIds, nodeTable) ;
                else
                {
                    // Makes nodes immediately.  Causing unecessary NodeTable accesses (e.g. project) 
                    Binding b = new BindingMap() ;
                    for ( Var v : bindingNodeIds )
                    {
                        NodeId id = bindingNodeIds.get(v) ;
                        Node n = nodeTable.retrieveNodeByNodeId(id) ;
                        b.add(v, n) ;
                    }
                    return b ;
                }
            }
        } ;
    }

    // Transform : Binding ==> BindingNodeId
    private static Transform<Binding, BindingNodeId> convFromBinding(final NodeTable nodeTable)
    {
        return new Transform<Binding, BindingNodeId>()
        {
            @Override
            public BindingNodeId convert(Binding binding)
            {
                if ( binding instanceof BindingTDB )
                    return ((BindingTDB)binding).getBindingId() ;
                
                BindingNodeId b = new BindingNodeId() ;
                @SuppressWarnings("unchecked")
                Iterator<Var> vars = (Iterator<Var>)binding.vars() ;
    
                for ( ; vars.hasNext() ; )
                {
                    Var v = vars.next() ;
                    Node n = binding.get(v) ;  
                    // Rely on the node table cache. 
                    NodeId id = nodeTable.nodeIdForNode(n) ;
                    b.put(v, id) ;
                }
                return b ;
            }
        } ;
    }

    /** Turn a BasicPattern into an abbreviated string for debugging */  
    public static String strPattern(BasicPattern pattern)
    {
        @SuppressWarnings("unchecked")
        List<Triple> triples = (List<Triple>)pattern.getList() ;
        String x = Iter.asString(triples, "\n  ") ;
        return printAbbrev(x) ; 
    }

}

/*
 * (c) Copyright 2008 Hewlett-Packard Development Company, LP
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */