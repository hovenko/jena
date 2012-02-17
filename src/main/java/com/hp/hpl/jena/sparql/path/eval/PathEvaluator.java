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

package com.hp.hpl.jena.sparql.path.eval;

import java.util.ArrayList ;
import java.util.Collection ;
import java.util.Iterator ;

import org.openjena.atlas.iterator.Filter ;
import org.openjena.atlas.iterator.Iter ;

import com.hp.hpl.jena.graph.Graph ;
import com.hp.hpl.jena.graph.Node ;
import com.hp.hpl.jena.graph.Triple ;
import com.hp.hpl.jena.sparql.path.P_Alt ;
import com.hp.hpl.jena.sparql.path.P_Distinct ;
import com.hp.hpl.jena.sparql.path.P_FixedLength ;
import com.hp.hpl.jena.sparql.path.P_Inverse ;
import com.hp.hpl.jena.sparql.path.P_Link ;
import com.hp.hpl.jena.sparql.path.P_Mod ;
import com.hp.hpl.jena.sparql.path.P_Multi ;
import com.hp.hpl.jena.sparql.path.P_NegPropSet ;
import com.hp.hpl.jena.sparql.path.P_OneOrMore ;
import com.hp.hpl.jena.sparql.path.P_ReverseLink ;
import com.hp.hpl.jena.sparql.path.P_Seq ;
import com.hp.hpl.jena.sparql.path.P_ZeroOrMore ;
import com.hp.hpl.jena.sparql.path.P_ZeroOrOne ;
import com.hp.hpl.jena.sparql.path.Path ;
import com.hp.hpl.jena.sparql.path.PathVisitor ;

final class PathEvaluator implements PathVisitor
{
    protected final Graph graph ;
    protected final Node node ;
    protected final Collection<Node> output ;
    private PathEngine engine ; 
    
    //protected abstract void eval(Graph graph, Node node, Path p, boolean forward) ;
    //protected abstract Iterator<Node> doOne(Node property) ;

    /** Evaluate a path */ 
    static void eval(Graph graph, Node node, Path path, PathEngine engine, Collection<Node> acc)
    {
        PathEvaluator evaluator = new PathEvaluator(graph, node, acc, engine) ;
        path.visit(evaluator) ;
    }

    /** Evaluate a path */ 
    static Iter<Node> eval(Graph graph, Node node, Path path, PathEngine engine)
    {
        Collection<Node> acc = new ArrayList<Node>() ;
        PathEvaluator evaluator = new PathEvaluator(graph, node, acc, engine) ;
        path.visit(evaluator) ;
        return Iter.iter(acc) ;
    }

    // Pass in graph, node only to PathEngine?
    protected PathEvaluator(Graph g, Node n, Collection<Node> output, PathEngine engine)
    {
        this.graph = g ; 
        this.node = n ;
        this.output = output ;
        this.engine = engine ;
    }
    
    // Overrides
    
    // Work functions.
    
    protected final void fill(Iterator<Node> iter)
    {
        for ( ; iter.hasNext() ; )
            output.add(iter.next()) ;
    }
    
    // These operations yield the same results regardless of counting
    // (their subpaths may not).

    @Override
    public void visit(P_Link pathNode)
    {
        Iterator<Node> nodes = engine.doOne(graph, node, pathNode.getNode()) ;
        fill(nodes) ;
    }
    
    @Override
    public void visit(P_ReverseLink pathNode)
    {
        engine.flipDirection() ;
        Iterator<Node> nodes = engine.doOne(graph, node, pathNode.getNode()) ;
        fill(nodes) ;
        engine.flipDirection() ;
    }
    
    @Override
    public void visit(P_Inverse inversePath)
    {
        //boolean b = forwardMode ;
        // Flip direction and evaluate
        engine.flipDirection() ;
        engine.eval(graph, inversePath.getSubPath(), node, output) ;
        engine.flipDirection() ;
    }
    
    @Override
    public void visit(P_NegPropSet pathNotOneOf)
    {
        engine.doNegatedPropertySet(pathNotOneOf, node, output) ;
    }

    @Override
    public void visit(P_Mod pathMod)
    {
        // do..Or.. need to take a visited set.
        
        if ( pathMod.isZeroOrMore() )
        {
            // :p{0,}
            engine.doOneOrMore(pathMod.getSubPath(), node, output) ;
            return ;
        }
        if ( pathMod.isOneOrMore() )
        {
            engine.doOneOrMore(pathMod.getSubPath(), node, output) ;
            return ;
        }
        
        if ( pathMod.isFixedLength() )
            engine.doFixedLengthPath(pathMod.getSubPath(), node, pathMod.getFixedLength(), output) ;
        else
            engine.doMultiLengthPath(pathMod.getSubPath(),
                              node,
                              pathMod.getMin(),
                              pathMod.getMax(), 
                              output) ;
    }

    @Override
    public void visit(P_FixedLength pFixedLength)
    {
        engine.doFixedLengthPath(pFixedLength.getSubPath(), node, pFixedLength.getCount(), output) ; 
    }
    
    @Override
    public void visit(P_ZeroOrOne path)
    {
        //WORK
        engine.doZeroOrOne(path.getSubPath(), node, output) ;
    }
    
    @Override
    public void visit(P_ZeroOrMore path)
    {
        engine.doZeroOrMore(path.getSubPath(), node, output) ;
    }
    
    @Override
    public void visit(P_OneOrMore path)
    {
        engine.doOneOrMore(path.getSubPath(), node, output) ;
    }
    
    
    
//    protected abstract void doZero(Path pathStep, Node node, Collection<Node> output) ;
//    protected abstract void doOne(Path pathStep, Node node, Collection<Node> output) ;

    
    @Override
    public void visit(P_Alt pathAlt)
    {
        engine.doAlt(pathAlt.getLeft(), pathAlt.getRight(), node, output) ;
    }
    
    @Override
    public void visit(P_Distinct pathDistinct)
    {
        PathEngine engine2 = engine ;
        engine = new PathEngine1(graph, engine.direction()) ;
        engine.eval(graph, pathDistinct.getSubPath(), node, output) ;
        engine = engine2 ;
    }

    @Override
    public void visit(P_Multi pathMulti)
    {
        PathEngine engine2 = engine ;
        engine = new PathEngineN(graph, engine.direction() ) ;
        engine.eval(graph, pathMulti.getSubPath(), node, output) ;
        engine = engine2 ;
    }

    @Override
    public void visit(P_Seq pathSeq)
    {
        engine.doSeq(pathSeq.getLeft(), pathSeq.getRight(), node, output) ;
    }
    
    // Other operations can produce duplicates and so may be executed in 
    // different ways depending on cardibnality requirements.  
    
    protected static class FilterExclude implements Filter<Triple>
    {
        private Collection<Node> excludes ;
        public FilterExclude(Collection <Node> excludes) { this.excludes = excludes ; }
        @Override
        public boolean accept(Triple triple)
        {
            return ! excludes.contains(triple.getPredicate()) ;
        }
    }
    
    final protected Iter<Triple> between(Node x, Node z)
    {
        Iter<Triple> iter1 = Iter.iter(graph.find(x, Node.ANY, z)) ;
        return iter1 ;
    }
    

    final protected void doZero(Path path, Node node, Collection<Node> output)
    {
        // Ignores path.
        output.add(node) ;
    }
    
}
