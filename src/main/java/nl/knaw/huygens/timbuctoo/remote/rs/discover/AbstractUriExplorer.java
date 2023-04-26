package nl.knaw.huygens.timbuctoo.remote.rs.discover;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.routing.RoutingSupport;
import org.apache.hc.core5.http.ClassicHttpResponse;

import javax.ws.rs.core.Response;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Execute a request against a server.
 */
public abstract class AbstractUriExplorer {
  private final CloseableHttpClient httpClient;
  private URI currentUri;

  public AbstractUriExplorer(CloseableHttpClient httpClient) {
    this.httpClient = httpClient;
  }

  static String getCharset(ClassicHttpResponse response) {
    try {
      String contentTypeCharset = response.getEntity().getContentType();
      if (contentTypeCharset != null && contentTypeCharset.indexOf(';') >= 0) {
        String charset = contentTypeCharset.substring(contentTypeCharset.indexOf(';') + 1).trim();
        return Charset.forName(charset).name();
      }
      return StandardCharsets.UTF_8.name();
    } catch (IllegalArgumentException e) {
      return StandardCharsets.UTF_8.name();
    }
  }

  public abstract Result<?> explore(URI uri, ResultIndex index, String authString);

  protected CloseableHttpClient getHttpClient() {
    return httpClient;
  }

  protected URI getCurrentUri() {
    return currentUri;
  }

  public <T> Result<T> execute(URI uri, ApplyException<ClassicHttpResponse, T, ?> func, String authString) {
    currentUri = uri;
    Result<T> result = new Result<T>(uri);
    HttpGet request = new HttpGet(uri);
    if (authString != null) {
      request.addHeader("Authorization", authString);
    }
    try (ClassicHttpResponse response =
                 httpClient.executeOpen(RoutingSupport.determineHost(request), request, null)) {
      int statusCode = response.getCode();
      result.setStatusCode(statusCode);
      if (!Response.Status.Family.SUCCESSFUL.equals(Response.Status.Family.familyOf(statusCode))) {
        result.addError(new RemoteException(statusCode, response.getReasonPhrase(), uri));
      } else {
        result.accept(func.apply(response));
      }
    } catch (Exception e) {
      result.addError(e);
    }
    return result;
  }

}
