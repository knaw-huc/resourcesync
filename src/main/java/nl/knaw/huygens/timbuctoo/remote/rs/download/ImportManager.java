package nl.knaw.huygens.timbuctoo.remote.rs.download;

import javax.ws.rs.core.MediaType;
import java.io.InputStream;
import java.util.concurrent.Future;

public interface ImportManager {

  public boolean isRdfTypeSupported(MediaType mediaType);

  // TODO: toevoegen aan ImportManager.addlog
  public Future<ImportStatus> addLog();

  public void addFile(InputStream fileStream, String fileName, MediaType mediaType);

}
