package org.nuxeo.labs.reference.hippo.operation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.labs.reference.constants.ExternalReferenceConstant;
import org.nuxeo.labs.reference.hippo.constants.ExternalReferenceConstantHippo;
import org.nuxeo.labs.reference.operation.AbstractExternalReferenceActions;
import org.nuxeo.runtime.api.Framework;

public class AbstractExternalReferenceHippoActions extends
        AbstractExternalReferenceActions {

    /**
     * Get a list of all Nuxeo documents references in Hippo from Hippo
     *
     * @return
     * @throws IOException
     */
    protected List<String> getAllDocumentRefsFromHippo() throws IOException {
        HttpURLConnection http = null;
        String restResult = "";
        List<String> result = new ArrayList<String>();

        URL theURL = new URL(ExternalReferenceConstantHippo.HIPPO_ENDPOINT);

        http = (HttpURLConnection) theURL.openConnection();
        http.setRequestMethod("GET");

        InputStream is = http.getInputStream();
        BufferedReader in = new BufferedReader(new InputStreamReader(is));

        StringBuffer sb = new StringBuffer();
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            sb.append(inputLine);
        }
        in.close();

        restResult = sb.toString();

        ObjectMapper resultAsObj = new ObjectMapper();
        JsonNode rootNode = resultAsObj.readTree(restResult);

        JsonNode uids = rootNode.get("ids");
        for (JsonNode uid : uids) {
            result.add(uid.getTextValue());
        }
        return result;

    }

    /**
     * Get the list of all Hippo references for one nuxeo document from Hippo
     *
     * @param documentUID
     * @return
     * @throws IOException
     */

    protected List<String> getRefsForDocumentFromHippo(String documentUID)
            throws IOException {

        HttpURLConnection http = null;
        String restResult = "";
        List<String> result = new ArrayList<String>();

        URL theURL = new URL(ExternalReferenceConstantHippo.HIPPO_ENDPOINT
                + documentUID);

        http = (HttpURLConnection) theURL.openConnection();
        http.setRequestMethod("GET");

        InputStream is = http.getInputStream();
        BufferedReader in = new BufferedReader(new InputStreamReader(is));

        StringBuffer sb = new StringBuffer();
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            sb.append(inputLine);
        }
        in.close();

        restResult = sb.toString();

        ObjectMapper resultAsObj = new ObjectMapper();
        JsonNode rootNode = resultAsObj.readTree(restResult);

        JsonNode urls = rootNode.get("urls");
        if (urls != null) {
            for (JsonNode url : urls) {
                result.add(url.getTextValue());
            }
        }
        return result;

    }

    protected DocumentModelList getAndStoreNuxeoDocumentRefsFromHippo(
            DocumentModel documentUID) throws IOException {

        DirectoryService dirService = Framework.getLocalService(DirectoryService.class);
        Session dirSession = dirService.open(ExternalReferenceConstant.EXTERNAL_REF_DIRECTORY);
        try {
            CoreSession coreSession = documentUID.getCoreSession();
            DocumentModelList proxies = coreSession.getProxies(
                    documentUID.getRef(), null);
            DocumentModelList newRefs = new DocumentModelListImpl();

            if (proxies.size() > 0) {
                for (DocumentModel proxy : proxies) {
                    List<String> hippoRefs = getRefsForDocumentFromHippo(proxy.getId());
                    for (String hippoRef : hippoRefs) {
                        newRefs.add(addExternalRef(dirSession,
                                documentUID.getId(), proxy.getId(), correctHippoLink(hippoRef),
                                extractHippoLabelFromLink(hippoRef), "Hippo US"));
                    }
                }
            }
            updateReferenceInfoForReporting(documentUID);
            return newRefs;
        } finally {
            dirSession.close();
        }
    }

    protected DocumentModelList updateHippoRefsOfNuxeoDocument(
            CoreSession coreSession, DocumentModel documentUID) throws IOException {
        removeExternalReference(coreSession,documentUID.getId(), null);
        return getAndStoreNuxeoDocumentRefsFromHippo(documentUID);

    }

    /**
     * Because the Hippo custom rest APIs has a bug
     *
     * @param wrongLink
     * @return
     */
    protected String correctHippoLink(String wrongLink) {
        String correctedLink = wrongLink;
        // Choosing a string to remove that should exclude other possible use of
        // Nuxeo in the name
        String toBeRemoved = ".com/nuxeo";
        if (correctedLink.contains(toBeRemoved)) {
            return correctedLink.replace(toBeRemoved, ".com");
        } else {
            return correctedLink;
        }
    }

    /**
     * Inventing a consistent label from the actual Ref
     * @param link
     * @return a Label
     */
   protected String extractHippoLabelFromLink(String link){
       return link.substring(link.lastIndexOf("/")+1,link.length()).replace("-", " ").replace(".html","");
   }

}
