/*
 * (C) Copyright 2012 Nuxeo SAS (http://nuxeo.com/) and contributors.
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

package org.nuxeo.ecm.platform.rendition.url;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.utils.StringUtils;
import org.nuxeo.common.utils.URIUtils;
import org.nuxeo.ecm.core.api.DocumentLocation;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.impl.DocumentLocationImpl;
import org.nuxeo.ecm.platform.url.DocumentViewImpl;
import org.nuxeo.ecm.platform.url.api.DocumentView;
import org.nuxeo.ecm.platform.url.service.AbstractDocumentViewCodec;

/**
 * Base class for Rendition url codec.
 * <p>
 * This class is shared with Template rendering system.
 * <p>
 * Codec handling a document repository, id, view and additional request
 * parameters. View is used to represent the Rendition name.
 * <p>
 * This codec supports both path abd id based urls.
 * 
 * @since 5.6
 * @author <a href="mailto:tdelprat@nuxeo.com">Tiry</a>
 * 
 */
public class RenditionBasedCodec extends AbstractDocumentViewCodec {

    protected static final Log log = LogFactory.getLog(DocumentRenditionCodec.class);

    public static final int URL_MAX_LENGTH = 2000;

    public static final String PATH_URL_PATTERN = "/" // slash
            + "([\\w\\.]+)" // server name (group 1)
            + "(?:/(.*))?" // path (group 2) (optional)
            + "@([\\w\\-\\.\\%]+)" // renditioName (group 3)
            + "/?" // final slash (optional)
            + "(?:\\?(.*)?)?";

    public static final String ID_URL_PATTERN = "/(\\w+)/([a-zA-Z_0-9\\-]+)(/([a-zA-Z_0-9\\-\\.]*))?(/)?(\\?(.*)?)?";

    public static String getRenditionUrl(DocumentModel doc, String renditionName) {
        DocumentView docView = new DocumentViewImpl(doc);
        docView.setViewId(renditionName);
        return new DocumentRenditionCodec().getUrlFromDocumentView(docView);
    }

    @Override
    public DocumentView getDocumentViewFromUrl(String url) {
        final Pattern pathPattern = Pattern.compile(getPrefix()
                + PATH_URL_PATTERN);
        Matcher pathMatcher = pathPattern.matcher(url);
        if (pathMatcher.matches()) {

            final String server = pathMatcher.group(1);
            String path = pathMatcher.group(2);
            if (path != null) {
                // add leading slash to make it absolute if it's not the root
                path = "/" + URIUtils.unquoteURIPathComponent(path);
            } else {
                path = "/";
            }
            final DocumentRef docRef = new PathRef(path);

            final String renditionName = URIUtils.unquoteURIPathComponent(pathMatcher.group(3));

            // get other parameters
            String query = pathMatcher.group(4);
            Map<String, String> params = URIUtils.getRequestParameters(query);
            final DocumentLocation docLoc = new DocumentLocationImpl(server,
                    docRef);
            return new DocumentViewImpl(docLoc, renditionName, params);
        } else {
            final Pattern idPattern = Pattern.compile(getPrefix()
                    + ID_URL_PATTERN);
            Matcher idMatcher = idPattern.matcher(url);
            if (idMatcher.matches()) {
                if (idMatcher.groupCount() >= 4) {

                    final String server = idMatcher.group(1);
                    String uuid = idMatcher.group(2);
                    final DocumentRef docRef = new IdRef(uuid);
                    final String renditionName = idMatcher.group(4);

                    // get other parameters

                    Map<String, String> params = null;
                    if (idMatcher.groupCount() > 6) {
                        String query = idMatcher.group(7);
                        params = URIUtils.getRequestParameters(query);
                    }

                    final DocumentLocation docLoc = new DocumentLocationImpl(
                            server, docRef);
                    return new DocumentViewImpl(docLoc, renditionName, params);
                }
            }
        }
        return null;
    }

    protected String getUrlFromDocumentViewWithId(DocumentView docView) {
        DocumentLocation docLoc = docView.getDocumentLocation();
        if (docLoc != null) {
            List<String> items = new ArrayList<String>();
            items.add(getPrefix());
            items.add(docLoc.getServerName());
            IdRef docRef = docLoc.getIdRef();
            if (docRef == null) {
                return null;
            }
            items.add(docRef.toString());
            String renditionName = docView.getViewId();
            if (renditionName != null) {
                items.add(renditionName);
            }
            String uri = StringUtils.join(items, "/");
            return URIUtils.addParametersToURIQuery(uri,
                    docView.getParameters());
        }
        return null;
    }

    @Override
    public String getUrlFromDocumentView(DocumentView docView) {

        // Use DocumentIdCodec if the document is a version
        if ("true".equals(docView.getParameter("version"))) {
            if (docView.getDocumentLocation().getIdRef() != null) {
                return getUrlFromDocumentViewWithId(docView);
            }
        }

        DocumentLocation docLoc = docView.getDocumentLocation();
        if (docLoc != null) {
            List<String> items = new ArrayList<String>();
            items.add(getPrefix());
            items.add(docLoc.getServerName());
            PathRef docRef = docLoc.getPathRef();
            if (docRef == null) {
                return null;
            }
            // this is a path, get rid of leading slash
            String path = docRef.toString();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.length() > 0) {
                items.add(URIUtils.quoteURIPathComponent(path, false));
            }
            String uri = StringUtils.join(items, "/");
            String renditionName = docView.getViewId();

            if (renditionName != null) {
                uri += "@"
                        + URIUtils.quoteURIPathComponent(renditionName, true);
            }

            String uriWithParam = URIUtils.addParametersToURIQuery(uri,
                    docView.getParameters());

            // If the URL with the Path codec is to long, it use the URL with
            // the Id Codec.
            if (uriWithParam.length() > URL_MAX_LENGTH) {

                // If the DocumentLocation did not contains the document Id, it
                // use the Path Codec even if the Url is too long for IE.
                if (null == docView.getDocumentLocation().getIdRef()) {
                    log.error("The DocumentLocation did not contains the RefId.");
                    return uriWithParam;
                }

                return getUrlFromDocumentViewWithId(docView);

            } else {
                return uriWithParam;
            }
        }
        return null;
    }
}