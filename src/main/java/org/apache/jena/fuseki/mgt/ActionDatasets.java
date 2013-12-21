/**
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

package org.apache.jena.fuseki.mgt;

import static java.lang.String.format ;

import java.io.IOException ;
import java.io.InputStream ;
import java.io.StringReader ;
import java.util.Iterator ;
import java.util.Locale ;

import javax.servlet.ServletException ;
import javax.servlet.ServletOutputStream ;
import javax.servlet.http.HttpServletRequest ;
import javax.servlet.http.HttpServletResponse ;

import org.apache.jena.atlas.io.IO ;
import org.apache.jena.atlas.json.JSON ;
import org.apache.jena.atlas.json.JsonBuilder ;
import org.apache.jena.atlas.json.JsonValue ;
import org.apache.jena.atlas.lib.InternalErrorException ;
import org.apache.jena.atlas.lib.StrUtils ;
import org.apache.jena.atlas.web.ContentType ;
import org.apache.jena.fuseki.FusekiLib ;
import org.apache.jena.fuseki.X_Config ;
import org.apache.jena.fuseki.server.* ;
import org.apache.jena.fuseki.servlets.ActionLib ;
import org.apache.jena.fuseki.servlets.HttpAction ;
import org.apache.jena.fuseki.servlets.ServletOps ;
import org.apache.jena.fuseki.servlets.Upload ;
import org.apache.jena.riot.* ;
import org.apache.jena.riot.lang.LangRIOT ;
import org.apache.jena.riot.system.ErrorHandler ;
import org.apache.jena.riot.system.ErrorHandlerFactory ;
import org.apache.jena.riot.system.StreamRDF ;
import org.apache.jena.riot.system.StreamRDFLib ;
import org.apache.jena.web.HttpSC ;

import com.hp.hpl.jena.datatypes.xsd.XSDDatatype ;
import com.hp.hpl.jena.graph.Node ;
import com.hp.hpl.jena.graph.NodeFactory ;
import com.hp.hpl.jena.query.Dataset ;
import com.hp.hpl.jena.query.ReadWrite ;
import com.hp.hpl.jena.rdf.model.* ;
import com.hp.hpl.jena.shared.uuid.JenaUUID ;
import com.hp.hpl.jena.sparql.core.DatasetGraph ;
import com.hp.hpl.jena.sparql.core.Quad ;
import com.hp.hpl.jena.sparql.util.FmtUtils ;
import com.hp.hpl.jena.tdb.transaction.DatasetGraphTransaction ;
import com.hp.hpl.jena.update.UpdateAction ;
import com.hp.hpl.jena.update.UpdateFactory ;
import com.hp.hpl.jena.update.UpdateRequest ;
import com.hp.hpl.jena.util.FileUtils ;

public class ActionDatasets extends ActionCtl {
    
    private static Dataset system = SystemState.getDataset() ;
    private static DatasetGraphTransaction systemDSG = SystemState.getDatasetGraph() ; 
    
    static private Property pServiceName = FusekiVocab.pServiceName ;
    static private Property pStatus = FusekiVocab.pStatus ;

    private static final String paramDatasetName    = "dbName" ;
    private static final String paramDatasetType    = "dbType" ;
    private static final String tDatabasetTDB       = "tdb" ;
    private static final String tDatabasetMem       = "mem" ;
    private static final String templateMemFN = "templates/config-mem" ;
    private static final String templateTDBFN = "templates/config-tdb" ;

    public ActionDatasets() { super() ; }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        doCommon(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        doCommon(request, response);
    }
    
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doCommon(request, response);
    }
    
//    @Override
//    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//        doCommon(request, response);
//    }

    @Override
    protected void perform(HttpAction action) {
        String method = action.request.getMethod().toUpperCase(Locale.ROOT) ;
        if ( method.equals("GET") )
            execGet(action) ;
        else if ( method.equals("POST") )
            execPost(action) ;
        else if ( method.equals("DELETE") )
            execDelete(action) ;
        else
            ServletOps.error(HttpSC.METHOD_NOT_ALLOWED_405) ;
    }

    protected void execGet(HttpAction action) {
        JsonValue v ;
        if (action.dsRef.name == null )
            v = execGetContainer(action) ;
        else
            v = execGetDataset(action) ;
        try {
            HttpServletResponse response = action.response ;
            ServletOutputStream out = response.getOutputStream() ;
            response.setContentType(WebContent.contentTypeJSON);
            response.setCharacterEncoding(WebContent.charsetUTF8) ;
            JSON.write(out, v) ;
            out.println() ; 
            out.flush() ;
            ServletOps.success(action);
        } catch (IOException ex) { ServletOps.errorOccurred(ex) ; }
    }
    
    // ---- GET : return details of dataset or datasets.
    
    private JsonValue execGetContainer(HttpAction action) { 
        action.log.info(format("[%d] GET datasets", action.id)) ;
        JsonBuilder builder = new JsonBuilder() ;
        builder.startObject("D") ;
        builder.key("datasets") ;
        JsonDescription.arrayDatasets(builder, DatasetRegistry.get());
        builder.finishObject("D") ;
        return builder.build() ;
    }

    private JsonValue execGetDataset(HttpAction action) {
        action.log.info(format("[%d] GET dataset %s", action.id, action.dsRef.name)) ;
        JsonBuilder builder = new JsonBuilder() ;
        String datasetPath = DatasetRef.canocialDatasetPath(action.dsRef.name) ;
        DatasetRef dsDesc = DatasetRegistry.get().get(datasetPath) ;
        if ( dsDesc == null )
            ServletOps.errorNotFound("Not found: dataset "+action.dsRef.name);
        JsonDescription.describe(builder, dsDesc) ;
        return builder.build() ;
    }
    
    // XXX Split contained and per-dataset operations into separate servlets? 
    // XXX ActionContainerItem

    // ---- POST 
    
    // POST /$/datasets/ -- to the container -> register new dataset
    // POST /$/datasets/name -- change something about an existing dataset
    
    protected void execPost(HttpAction action) {
        if (action.dsRef.name == null )
            execPostContainer(action) ;
        else
            execPostDataset(action) ;
    }
    
    private void execPostDataset(HttpAction action) {
        String name = action.dsRef.name ;
        if ( name == null )
            name = "" ;
        action.log.info(format("[%d] POST dataset %s", action.id, name)) ;
        
        if ( action.dsRef.dataset == null )
            ServletOps.errorNotFound("Not found: dataset "+action.dsRef.name);
        DatasetRef dsDesc = action.dsRef ;
        String s = action.request.getParameter("state") ;
        if ( s == null || s.isEmpty() )
            ServletOps.errorBadRequest("No state change given") ;

        // setDatasetState is a transaction on the persistent state of the server. 
        if ( s.equalsIgnoreCase("active") ) {
            setDatasetState(name, FusekiVocab.stateActive) ;        
            dsDesc.activate() ;
        } else if ( s.equalsIgnoreCase("dormant") ) {
            setDatasetState(name, FusekiVocab.stateDormant) ;        
            dsDesc.dormant() ;
        } else
            ServletOps.errorBadRequest("New state '"+s+"' not recognized");
        ServletOps.success(action) ;
    }

    private void execPostContainer(HttpAction action) {

        String newURI = JenaUUID.generate().asURI() ;
        Node gn = NodeFactory.createURI(newURI) ;
        
        ContentType ct = FusekiLib.getContentType(action) ;
        
        boolean committed = false ;
        system.begin(ReadWrite.WRITE) ;
        try {
            Model model = system.getNamedModel(gn.getURI()) ;
            StreamRDF dest = StreamRDFLib.graph(model.getGraph()) ;

            if ( WebContent.isHtmlForm(ct) )
                assemblerFromForm(action, dest) ;
            else if ( WebContent.isMultiPartForm(ct) )
                assemblerFromUpload(action, dest) ;
            else
                assemblerFromBody(action, dest) ;
            
            Statement stmt = getOne(model, null, pServiceName, null) ;
            if ( stmt == null ) {
                StmtIterator sIter = model.listStatements(null, pServiceName, (RDFNode)null ) ;
                if ( ! sIter.hasNext() )
                    ServletOps.errorBadRequest("No name given in description of Fuseki service") ;
                sIter.next() ;
                if ( sIter.hasNext() )
                    ServletOps.errorBadRequest("Multiple names given in description of Fuseki service") ;
                throw new InternalErrorException("Inconsistent: getOne didn't fail the second time") ;
            }
                
            if ( ! stmt.getObject().isLiteral() )
                ServletOps.errorBadRequest("Found "+FmtUtils.stringForRDFNode(stmt.getObject())+" : Service names are strings, then used to build the external URI") ;
            
            Resource subject = stmt.getSubject() ;
            Literal object = stmt.getObject().asLiteral() ;
            
            if ( object.getDatatype() != null && ! object.getDatatype().equals(XSDDatatype.XSDstring) )
                action.log.warn(format("[%d] Service name '%s' is not a string", action.id, FmtUtils.stringForRDFNode(object)));

            String datasetName = object.getLexicalForm() ;
            String datasetPath = DatasetRef.canocialDatasetPath(datasetName) ;
            action.log.info(format("[%d] Create database : name = %s", action.id, datasetPath)) ;
            
            if ( DatasetRegistry.get().isRegistered(datasetPath) )
                // And abort.
                ServletOps.error(HttpSC.CONFLICT_409, "Name already registered "+datasetName) ;
                
            model.removeAll(null, pStatus, null) ;
            model.add(subject, pStatus, FusekiVocab.stateActive) ;
            
            // Need to be in Resource space at this point.
            DatasetRef dsRef = FusekiConfig.processService(subject) ;
            X_Config.registerDataset(datasetPath, dsRef) ;
            action.getResponse().setContentType(WebContent.contentTypeTextPlain); 
            ServletOutputStream out = action.getResponse().getOutputStream() ;
            out.println("That went well") ;
            ServletOps.success(action) ;
            system.commit();
            committed = true ;
            
        } catch (IOException ex) { IO.exception(ex); }
        finally { 
            if ( ! committed ) system.abort() ; 
            system.end() ; 
        }
    }
    
    private void assemblerFromBody(HttpAction action, StreamRDF dest) {
        bodyAsGraph(action, dest) ;
    }

    private void assemblerFromForm(HttpAction action, StreamRDF dest) {
//        Enumeration<String> en = action.getRequest().getParameterNames() ;
//        while( en.hasMoreElements() ) {
//            String pn = en.nextElement() ;
//            System.out.println(pn) ;
//        }
        
        String dbType = action.getRequest().getParameter(paramDatasetType) ;
        String dbName = action.getRequest().getParameter(paramDatasetName) ;
        action.log.info(format("[%d] Create database : name = %s, type = %s", action.id, dbName, dbType )) ;
        if ( dbType == null || dbName == null )
            ServletOps.errorBadRequest("Required parameters: dbName and dbType");
        if ( ! dbType.equals(tDatabasetTDB) && ! dbType.equals(tDatabasetMem) )
            ServletOps.errorBadRequest(format("dbType can be only '%s' or '%s'", tDatabasetTDB, tDatabasetMem)) ;
        
        String template = null ;
        try {
            if ( dbType.equals(tDatabasetTDB))
                template = FileUtils.readWholeFileAsUTF8(templateTDBFN) ;
            if ( dbType.equals(tDatabasetMem))
                template = FileUtils.readWholeFileAsUTF8(templateMemFN) ;
        } catch (IOException ex) { IO.exception(ex); }

        // XXX Be careful.
        template = template.replaceAll("\\{NAME\\}", dbName) ;
        RDFDataMgr.parse(dest, new StringReader(template), "http://base/", Lang.TTL) ;
    }

    // ---- DELETE

    private void assemblerFromUpload(HttpAction action, StreamRDF dest) {
        Upload.fileUploadWorker(action, dest, true);
    }

    protected void execDelete(HttpAction action) {
        // Does not exist?
        String name = action.dsRef.name ;
        if ( name == null )
            name = "" ;
        action.log.info(format("[%d] DELETE ds=%s", action.id, name)) ;
        if ( action.dsRef.name == null ) {
            ServletOps.errorBadRequest("DELETE only applies to a specific dataset.") ;
            return ;
        }
        
        if ( ! DatasetRegistry.get().isRegistered(name) )
            ServletOps.errorNotFound("No such dataset registered: "+name);

        systemDSG.begin(ReadWrite.WRITE) ;
        boolean committed =false ;
        try {
            DatasetRef dsRef = DatasetRegistry.get().get(name) ;
            // Redo check inside transaction.
            if ( dsRef == null )
                ServletOps.errorNotFound("No such dataset registered: "+name);
                
            // Name to graph
            Quad q = getOne(SystemState.getDatasetGraph(), null, null, pServiceName.asNode(), null) ;
            if ( q == null )
                ServletOps.errorBadRequest("Failed to find dataset for '"+name+"'");
            Node gn = q.getGraph() ;

            dsRef.gracefulShutdown() ;
            DatasetRegistry.get().remove(name) ;
            // XXX or set to state deleted.
            systemDSG.deleteAny(gn, null, null, null) ;
            systemDSG.commit() ;
            committed = true ;
            ServletOps.success(action) ;
        } finally { 
            if ( ! committed ) systemDSG.abort() ; 
            systemDSG.end() ; 
        }
    }

    // Persistent state change.
    private void setDatasetState(String name, Resource newState) {
        boolean committed = false ;
        system.begin(ReadWrite.WRITE) ;
        try {
            String dbName = name ;
            if ( dbName.startsWith("/") )
                dbName = dbName.substring(1) ;
            
            String update =  StrUtils.strjoinNL
                (SystemState.PREFIXES,
                 "DELETE { GRAPH ?g { ?s fu:status ?state } }",
                 "INSERT { GRAPH ?g { ?s fu:status "+FmtUtils.stringForRDFNode(newState)+" } }",
                 "WHERE {",
                 "   GRAPH ?g { ?s fu:name '"+dbName+"' ; ",
                 "                 fu:status ?state .",
                 "   }",
                 "}"
                 ) ;
            UpdateRequest req =  UpdateFactory.create(update) ;
            UpdateAction.execute(req, system);
            system.commit();
            committed = true ;
        } finally { 
            if ( ! committed ) system.abort() ; 
            system.end() ; 
        }
    }
    
    // ---- Auxilary functions

    private static Quad getOne(DatasetGraph dsg, Node g, Node s, Node p, Node o) {
        Iterator<Quad> iter = dsg.findNG(g, s, p, o) ;
        if ( ! iter.hasNext() )
            return null ;
        Quad q = iter.next() ;
        if ( iter.hasNext() )
            return null ;
        return q ;
    }
    
    private static Statement getOne(Model m, Resource s, Property p, RDFNode o) {
        StmtIterator iter = m.listStatements(s, p, o) ;
        if ( ! iter.hasNext() )
            return null ;
        Statement stmt = iter.next() ;
        if ( iter.hasNext() )
            return null ;
        return stmt ;
    }
    
    // XXX Merge with Upload.incomingData
    
    private static void bodyAsGraph(HttpAction action, StreamRDF dest) {
        HttpServletRequest request = action.request ;
        String base = ActionLib.wholeRequestURL(request) ;
        ContentType ct = FusekiLib.getContentType(request) ;
        Lang lang = RDFLanguages.contentTypeToLang(ct.getContentType()) ;
        if ( lang == null ) {
            ServletOps.errorBadRequest("Unknown content type for triples: " + ct) ;
            return ;
        }
        InputStream input = null ;
        try { input = request.getInputStream() ; } 
        catch (IOException ex) { IO.exception(ex) ; }

        int len = request.getContentLength() ;
//        if ( verbose ) {
//            if ( len >= 0 )
//                alog.info(format("[%d]   Body: Content-Length=%d, Content-Type=%s, Charset=%s => %s", action.id, len,
//                                ct.getContentType(), ct.getCharset(), lang.getName())) ;
//            else
//                alog.info(format("[%d]   Body: Content-Type=%s, Charset=%s => %s", action.id, ct.getContentType(),
//                                ct.getCharset(), lang.getName())) ;
//        }
        dest.prefix("root", base+"#");
        parse(action, dest, input, lang, base) ;
         
    }

    // See SPARQL_REST for common code.
    public static void parse(HttpAction action, StreamRDF dest, InputStream input, Lang lang, String base) {
        // Need to adjust the error handler.
//        try { RDFDataMgr.parse(dest, input, base, lang) ; }
//        catch (RiotException ex) { errorBadRequest("Parse error: "+ex.getMessage()) ; }
        LangRIOT parser = RiotReader.createParser(input, lang, base, dest) ;
        ErrorHandler errorHandler = ErrorHandlerFactory.errorHandlerStd(action.log) ;
        parser.getProfile().setHandler(errorHandler) ;
        try { parser.parse() ; } 
        catch (RiotException ex) { ServletOps.errorBadRequest("Parse error: "+ex.getMessage()) ; }
    }
}
