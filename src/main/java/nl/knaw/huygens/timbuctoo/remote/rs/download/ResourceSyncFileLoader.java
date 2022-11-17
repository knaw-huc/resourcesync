package nl.knaw.huygens.timbuctoo.remote.rs.download;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.google.common.collect.ImmutableMap;
import nl.knaw.huygens.timbuctoo.remote.rs.download.exceptions.CantRetrieveFileException;
import nl.knaw.huygens.timbuctoo.remote.rs.xml.Capability;
import nl.knaw.huygens.timbuctoo.remote.rs.xml.RsLn;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Browses the resourcesync files and loads all resources that are referenced
 * <p/>
 * A resource might not be downloadable, in that case loadFiles will throw and abort in an unfinished state.
 * You can provide a httpClient that automatically retries failed requests and that waits a long time
 * before returning a timeout to limit the amount of failures.
 */
public class ResourceSyncFileLoader {
  private static final Logger LOG = getLogger(ResourceSyncFileLoader.class);
  private static final Map<String, String> MIME_TYPE_FOR_EXTENSION = ImmutableMap.<String, String>builder()
    .put("ttl", "text/turtle")
    .put("rdf", "application/rdf+xml")
    .put("nt", "application/n-triples")
    .put("jsonld", "application/ld+json")
    .put("owl", "application/owl+xml")
    .put("trig", "application/trig")
    .put("nq", "application/n-quads")
    .put("trix", "application/trix+xml")
    .put("trdf", "application/rdf+thrift")
    .put("nqud", "application/vnd.timbuctoo-rdf.nquads_unified_diff")
    .build();

  private final RemoteFileRetriever remoteFileRetriever;
  private final ObjectMapper objectMapper;

  public ResourceSyncFileLoader() {
    this(HttpClients.createMinimal());
  }

  public ResourceSyncFileLoader(int timeout) {
    this(HttpClients.createMinimal(), timeout);
  }

  public ResourceSyncFileLoader(CloseableHttpClient httpClient) {
    this(new RemoteFileRetriever(httpClient, 0));
  }

  public ResourceSyncFileLoader(CloseableHttpClient httpClient, int timeout) {
    this(new RemoteFileRetriever(httpClient, timeout));
  }

  public ResourceSyncFileLoader(RemoteFileRetriever remoteFileRetriever) {
    objectMapper = new XmlMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    this.remoteFileRetriever = remoteFileRetriever;
  }

  //FIXME: maybe we should just store everything compressed
  public static InputStream maybeDecompress(InputStream input) throws IOException {
    final PushbackInputStream pb = new PushbackInputStream(input, 2);
    int header = pb.read();
    if (header == -1) {
      return pb;
    }

    int firstByte = pb.read();
    if (firstByte == -1) {
      pb.unread(header);
      return pb;
    }

    pb.unread(new byte[]{(byte) header, (byte) firstByte});

    header = (firstByte << 8) | header;

    if (header == GZIPInputStream.GZIP_MAGIC) {
      return new GZIPInputStream(pb);
    } else {
      return pb;
    }
  }

  public RemoteFilesList getRemoteFilesList(String capabilityListUri, String authString)
    throws IOException, CantRetrieveFileException {
    List<UrlItem> capabilityList = getRsFile(capabilityListUri, authString).getItemList();

    List<RemoteFile> changes = new ArrayList<>();
    List<RemoteFile> resources = new ArrayList<>();

    for (UrlItem capabilityListItem : capabilityList) {
      if (capabilityListItem.getMetadata().getCapability().equals(Capability.CHANGELIST.getXmlValue())) {
        UrlSet rsFile = getRsFile(capabilityListItem.getLoc(), authString);

        String changeListExtension = ".*.nqud";
        for (UrlItem changeListItem : rsFile.getItemList()) {
          RsLn changeLink = changeListItem.getLink();
          if (changeLink != null) {
            if ((changeLink.getType().isPresent() &&
              changeLink.getType().get().equals(MIME_TYPE_FOR_EXTENSION.get("nqud"))) ||
              changeLink.getHref().matches(changeListExtension)) {
              Metadata changeMd = new Metadata();
              changeMd.setMimeType(changeLink.getType().get());
              changeMd.setDateTime(changeListItem.getMetadata().getDateTime());
              RemoteFile remoteFile = getRemoteFile(changeLink.getHref(), changeMd, authString);
              changes.add(remoteFile);
            }
          }
        }
      } else if (capabilityListItem.getMetadata().getCapability().equals(Capability.RESOURCELIST.getXmlValue())) {
        UrlSet rsFile = getRsFile(capabilityListItem.getLoc(), authString);

        for (UrlItem resourceListItem : rsFile.getItemList()) {
          if (MIME_TYPE_FOR_EXTENSION.containsValue(resourceListItem.getMetadata().getMimeType()) ||
            SupportedRdfResourceListExtensions.createFromFile(resourceListItem.getLoc()) != null) {
            resources.add(getRemoteFile(resourceListItem.getLoc(), resourceListItem.getMetadata(), authString));
          }
        }
      }
    }

    return new RemoteFilesList(changes, resources);
  }

  private RemoteFile getRemoteFile(String href, Metadata metadata, String authString) {
    return RemoteFile.create(href, () -> remoteFileRetriever.getFile(href, authString),
        getUrl(href, metadata), metadata);
  }

  private String getUrl(String href, Metadata metadata) {
    if (metadata != null) {
      return metadata.getMimeType();
    } else {
      String extension = href.substring(href.lastIndexOf(".") + 1);
      return MIME_TYPE_FOR_EXTENSION.get(extension);
    }
  }

  private UrlSet getRsFile(String url, String authString) throws IOException, CantRetrieveFileException {
    LOG.info("getRsFile '{}'", url);
    return objectMapper.readValue(
            IOUtils.toString(remoteFileRetriever.getFile(url, authString), StandardCharsets.UTF_8),
            UrlSet.class);
  }

  private enum SupportedRdfResourceListExtensions {
    NQ("nq"),
    TRIG("trig"),
    NT("nt"),
    TTL("ttl"),
    N3("n3"),
    RDF_XML("xml"),
    JSONLD("jsonld"),;

    private final String extension;

    SupportedRdfResourceListExtensions(String extension) {
      this.extension = extension;
    }

    public static SupportedRdfResourceListExtensions createFromFile(String fileName) {
      for (SupportedRdfResourceListExtensions rdfExtensions : SupportedRdfResourceListExtensions.values()) {
        if (fileName.endsWith("." + rdfExtensions.getExtension())) {
          return rdfExtensions;
        }
      }
      return null;
    }

    public String getExtension() {
      return extension;
    }
  }

  static class RemoteFilesList {
    private final List<RemoteFile> changeList;
    private final List<RemoteFile> resourceList;

    public RemoteFilesList(List<RemoteFile> changeList, List<RemoteFile> resourceList) {
      this.changeList = changeList;
      this.resourceList = resourceList;
    }

    public List<RemoteFile> getChangeList() {
      return changeList;
    }

    public List<RemoteFile> getResourceList() {
      return resourceList;
    }
  }

  static class RemoteFileRetriever {
    private static final Logger LOG = getLogger(RemoteFileRetriever.class);
    private final CloseableHttpClient httpClient;
    private final int timeout;

    private RemoteFileRetriever(CloseableHttpClient httpClient) {
      this.httpClient = httpClient;
      this.timeout = 0;
    }

    private RemoteFileRetriever(CloseableHttpClient httpClient, int timeout) {
      this.httpClient = httpClient;
      this.timeout = timeout;
    }

    public InputStream getFile(String url, String authString)
      throws CantRetrieveFileException, IOException {
      CloseableHttpClient httpClient = HttpClients.createDefault();
      HttpGet httpGet = new HttpGet(url);

      // Timeout time is set to 100 seconds to prevent socket timeout during changelist import
      if (timeout > 0) {
        httpGet.setConfig(RequestConfig.custom().setSocketTimeout(timeout).build());
      }
      if (authString != null) {
        httpGet.addHeader("Authorization", authString);
      }

      LOG.info("Calling " + url);
      HttpResponse httpResponse = httpClient.execute(httpGet);
      LOG.info("Got response from " + url);
      if (httpResponse.getStatusLine().getStatusCode() == 200) {
        InputStream content = httpResponse.getEntity().getContent();
        if (content != null) {
          return maybeDecompress(content);
        } else {
          return new ByteArrayInputStream(new byte[0]);
        }
      }

      throw new CantRetrieveFileException(httpResponse.getStatusLine().getStatusCode(),
        httpResponse.getStatusLine().getReasonPhrase());
    }
  }
}
