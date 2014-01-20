/*
 * (C) Copyright 2010 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * Contributors:
 * Nuxeo - initial API and implementation
 */

package org.nuxeo.ecm.platform.rendition.publisher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.platform.rendition.publisher.RenditionPublicationFactory.RENDITION_NAME_PARAMETER_KEY;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.impl.DocumentLocationImpl;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.ACP;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.BackendType;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.ecm.platform.publisher.api.PublicationNode;
import org.nuxeo.ecm.platform.publisher.api.PublicationTree;
import org.nuxeo.ecm.platform.publisher.api.PublishedDocument;
import org.nuxeo.ecm.platform.publisher.api.PublisherService;
import org.nuxeo.ecm.platform.publisher.impl.core.SimpleCorePublishedDocument;
import org.nuxeo.ecm.platform.publisher.task.CoreProxyWithWorkflowFactory;
import org.nuxeo.ecm.platform.task.test.TaskUTConstants;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;

import com.google.inject.Inject;

/**
 *
 * @author <a href="mailto:tdelprat@nuxeo.com">Tiry</a>
 *
 */
@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@RepositoryConfig(type = BackendType.H2, init = RenditionPublicationRepositoryInit.class, user = "Administrator", cleanup = Granularity.METHOD)
@Deploy({ "org.nuxeo.ecm.core.convert.api", "org.nuxeo.ecm.core.convert",
        "org.nuxeo.ecm.core.convert.plugins", "org.nuxeo.ecm.platform.convert",
        "org.nuxeo.ecm.platform.rendition.api",
        "org.nuxeo.ecm.platform.rendition.core",
        "org.nuxeo.ecm.automation.core", "org.nuxeo.ecm.directory",
        "org.nuxeo.ecm.directory.sql", "org.nuxeo.ecm.directory.types.contrib",
        "org.nuxeo.ecm.platform.versioning.api",
        "org.nuxeo.ecm.platform.versioning", "org.nuxeo.ecm.relations",
        "org.nuxeo.ecm.relations.jena",
        "org.nuxeo.ecm.platform.publisher.core.contrib",
        "org.nuxeo.ecm.platform.publisher.core",
        "org.nuxeo.ecm.platform.publisher.task",
        TaskUTConstants.CORE_BUNDLE_NAME, TaskUTConstants.TESTING_BUNDLE_NAME,
        "org.nuxeo.ecm.platform.rendition.publisher" })
@LocalDeploy({
        "org.nuxeo.ecm.platform.rendition.publisher:relations-default-jena-contrib.xml",
        "org.nuxeo.ecm.platform.rendition.publisher:test-sql-directories-contrib.xml",
        "org.nuxeo.ecm.platform.rendition.publisher:publishing-facets.xml"})
public class TestRenditionPublicationWFReject {

    @Inject
    protected CoreSession session;

    @Inject
    protected PublisherService publisherService;

    @Inject
    protected DirectoryService directoryService;

    @Inject
    protected EventService eventService;

    protected HashMap<String, String> factoryParams = new HashMap<String, String>();

    protected DocumentModel doc2Publish = null;

    @Before
    public void initPublishTestCase() throws Exception {
        if (doc2Publish != null) {
            session.removeChildren(session.getRootDocument().getRef());
            eventService.waitForAsyncCompletion();
            doc2Publish = null;
        }

        if (doc2Publish == null) {
            doc2Publish = createDocumentToPublish();
            initializeACP();
            factoryParams.put(
                    CoreProxyWithWorkflowFactory.LOOKUP_STATE_PARAM_KEY,
                    CoreProxyWithWorkflowFactory.LOOKUP_STATE_PARAM_BYTASK);
        }
    }

    private DocumentModel createDocumentToPublish() throws Exception {
        DocumentModel wsRoot = session.getDocument(new PathRef(
                "default-domain/workspaces"));

        DocumentModel ws = session.createDocumentModel(
                wsRoot.getPathAsString(), "ws1", "Workspace");
        ws.setProperty("dublincore", "title", "test WS");
        ws = session.createDocument(ws);

        DocumentModel doc2Publish = session.createDocumentModel(
                ws.getPathAsString(), "file", "File");
        doc2Publish.setProperty("dublincore", "title", "MyDoc");

        Blob blob = new StringBlob("SomeDummyContent");
        blob.setFilename("dummyBlob.txt");
        blob.setMimeType("text/plain");
        doc2Publish.setProperty("file", "content", blob);

        doc2Publish = session.createDocument(doc2Publish);

        session.save();
        return doc2Publish;
    }

    private void initializeACP() throws Exception {

        DocumentModel root = session.getRootDocument();
        ACP acp = session.getACP(root.getRef());
        ACL existingACL = acp.getOrCreateACL();
        existingACL.clear();
        existingACL.add(new ACE("myuser1", SecurityConstants.READ, true));
        existingACL.add(new ACE("myuser2", SecurityConstants.READ, true));
        existingACL.add(new ACE("myuser3", SecurityConstants.READ, true));
        existingACL.add(new ACE("myuser4", SecurityConstants.READ, true));
        acp.addACL(existingACL);
        session.setACP(root.getRef(), acp, true);

        DocumentModel ws1 = session.getDocument(new PathRef(
                "default-domain/workspaces/ws1"));
        acp = session.getACP(ws1.getRef());
        existingACL = acp.getOrCreateACL();
        existingACL.clear();
        existingACL.add(new ACE("myuser1", SecurityConstants.READ_WRITE, true));
        existingACL.add(new ACE("myuser2", SecurityConstants.EVERYTHING, true));
        acp.addACL(existingACL);
        session.setACP(ws1.getRef(), acp, true);

        DocumentModel section1 = session.getDocument(new PathRef(
                "default-domain/sections/section"));

        acp = session.getACP(section1.getRef());
        existingACL = acp.getOrCreateACL();
        existingACL.clear();
        existingACL.add(new ACE("myuser1", SecurityConstants.READ, true));
        existingACL.add(new ACE("myuser2", SecurityConstants.EVERYTHING, true));
        acp.addACL(existingACL);
        session.setACP(section1.getRef(), acp, true);

        session.save();
    }

    private void changeUser(String userName) throws Exception {
        Session userdir = directoryService.open("userDirectory");
        DocumentModel userModel = userdir.getEntry(userName);
        // set it on session
        NuxeoPrincipal originalUser = (NuxeoPrincipal) session.getPrincipal();
        originalUser.setModel(userModel);
        originalUser.setName(userName);
    }

    @Test
    public void testRejectRenditionPublication() throws Exception {

        changeUser("myuser1");
        String defaultTreeName = publisherService.getAvailablePublicationTree().get(
                0);
        PublicationTree treeUser1 = publisherService.getPublicationTree(
                defaultTreeName, session, factoryParams);

        List<PublicationNode> nodes = treeUser1.getChildrenNodes();
        assertEquals(1, nodes.size());
        assertEquals("Section1", nodes.get(0).getTitle());

        PublicationNode targetNode = nodes.get(0);

        PublishedDocument publishedDocument = treeUser1.publish(doc2Publish,
                targetNode,
                Collections.singletonMap(RENDITION_NAME_PARAMETER_KEY, "pdf"));
        assertTrue(publishedDocument.isPending());
        assertEquals(
                1,
                treeUser1.getExistingPublishedDocument(
                        new DocumentLocationImpl(doc2Publish)).size());

        // myuser3 can't see the document waiting for validation
        changeUser("myuser3");
        session.save(); // Save session to get modifications made by other
        // sessions
        PublicationTree treeUser3 = publisherService.getPublicationTree(
                defaultTreeName, session, factoryParams);
        assertEquals(
                0,
                treeUser3.getExistingPublishedDocument(
                        new DocumentLocationImpl(doc2Publish)).size());

        // myuser2 can see it, it's the validator
        changeUser("myuser2");
        PublicationTree treeUser2 = publisherService.getPublicationTree(
                defaultTreeName, session, factoryParams);
        List<PublishedDocument> publishedDocuments = treeUser2.getExistingPublishedDocument(new DocumentLocationImpl(
                doc2Publish));
        assertEquals(1, publishedDocuments.size());

        publishedDocument = publishedDocuments.get(0);
        assertTrue(publishedDocument.isPending());

        assertTrue(treeUser2.canManagePublishing(publishedDocument));
        assertTrue(treeUser2.hasValidationTask(publishedDocument));
        treeUser2.validatorRejectPublication(publishedDocument, "Rejected!");
        assertTrue(publishedDocument.isPending());
        // No more document to approve
        assertEquals(
                0,
                treeUser2.getExistingPublishedDocument(
                        new DocumentLocationImpl(doc2Publish)).size());

        // published so myuser3 still can't see it
        changeUser("myuser3");
        session.save(); // Save session to get modifications made by other
        // sessions (here, removing workflow ACL)
        assertEquals(
                0,
                treeUser3.getExistingPublishedDocument(
                        new DocumentLocationImpl(doc2Publish)).size());

        // No more document published for myuser1
        changeUser("myuser1");
        assertEquals(
                0,
                treeUser1.getExistingPublishedDocument(
                        new DocumentLocationImpl(doc2Publish)).size());

        DocumentModel proxy = ((SimpleCorePublishedDocument) publishedDocument).getProxy();
        assertFalse(session.exists(proxy.getRef()));
    }

}
