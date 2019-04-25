package nl.knaw.huygens.timbuctoo.remote.rs.download;

import javax.ws.rs.core.MediaType;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.concurrent.Future;

public interface ImportManager {

  public boolean isRdfTypeSupported(MediaType mediaType);

  // TODO: toevoegen aan ImportManager.addlog
  public Future<ImportStatus> addLog(String baseUri, String defaultGraph, String fileName,
                                     InputStream rdfInputStream,
                                     Optional<Charset> charset, MediaType mediaType);

  public void addFile(InputStream fileStream, String fileName, MediaType mediaType);

}
