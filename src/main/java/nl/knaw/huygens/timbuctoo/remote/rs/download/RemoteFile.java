package nl.knaw.huygens.timbuctoo.remote.rs.download;

import nl.knaw.huygens.timbuctoo.remote.rs.download.exceptions.CantRetrieveFileException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class RemoteFile {
  private final String url;
  private final RemoteData data;
  private final String mimeType;
  private final Metadata metadata;

  public RemoteFile(String url, RemoteData data, String mimeType, Metadata metadata) {
    this.url = url;
    this.data = data;
    this.mimeType = mimeType;
    this.metadata = metadata;
  }

  public String getUrl() {
    return url;
  }

  public RemoteData getData() {
    return data;
  }

  public String getMimeType() {
    return mimeType;
  }

  public Metadata getMetadata() {
    return metadata;
  }

  static RemoteFile create(String url, RemoteData data, String mimeType, Metadata metadata) {
    return new RemoteFile(url, data, mimeType, metadata);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    RemoteFile that = (RemoteFile) o;
    return Objects.equals(url, that.url) &&
            Objects.equals(data, that.data) &&
            Objects.equals(mimeType, that.mimeType) &&
            Objects.equals(metadata, that.metadata);
  }

  @Override
  public int hashCode() {
    return Objects.hash(url, data, mimeType, metadata);
  }

  interface RemoteData {
    InputStream get() throws IOException, CantRetrieveFileException;
  }
}
