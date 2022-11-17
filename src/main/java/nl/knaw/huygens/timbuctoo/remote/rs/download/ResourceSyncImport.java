package nl.knaw.huygens.timbuctoo.remote.rs.download;

import nl.knaw.huygens.timbuctoo.remote.rs.download.ResourceSyncFileLoader.RemoteFilesList;
import nl.knaw.huygens.timbuctoo.remote.rs.download.exceptions.CantRetrieveFileException;
import nl.knaw.huygens.timbuctoo.remote.rs.download.exceptions.CantDetermineDataSetException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Future;

public class ResourceSyncImport {
  private static final Logger LOG = LoggerFactory.getLogger(ResourceSyncFileLoader.class);
  private final ResourceSyncFileLoader resourceSyncFileLoader;
  private final boolean async;

  public ResourceSyncImport(ResourceSyncFileLoader resourceSyncFileLoader, boolean async) {
    this.resourceSyncFileLoader = resourceSyncFileLoader;
    this.async = async;
  }

  public void filterAndImport(String capabilityListUri, String userSpecifiedDataSet, String authString,
                              Date lastUpdate, WithFile withFile)
      throws CantDetermineDataSetException, IOException, CantRetrieveFileException {
    List<RemoteFile> filesToImport;

    if (userSpecifiedDataSet == null) {
      filesToImport = filter(capabilityListUri, authString, lastUpdate);
    } else {
      filesToImport = filter(capabilityListUri, userSpecifiedDataSet, authString);
    }

    Iterator<RemoteFile> files = filesToImport.iterator();

    // Get total number of files found
    int numOfFiles = filesToImport.size();
    LOG.info("Found '{}' files to be processed", numOfFiles);

    if (!files.hasNext()) {
      LOG.error("No supported files available for import.");
      return;
    }

    int fileCounter = 0;
    try {
      while (files.hasNext()) {
        fileCounter++;
        RemoteFile file = files.next();
        LOG.info("Processing file {} out of {}, location: {}  ", fileCounter, numOfFiles, file.getUrl());

        Future<?> fileFuture = withFile.withFile(
            file.getData().get(), file.getUrl(), file.getMimeType(), file.getMetadata().getDateTime());

        if (!async) {
          fileFuture.get();
        }
      }
    } catch (Exception e) {
      LOG.error("Could not read files to import", e);
    }
  }

  private List<RemoteFile> filter(String capabilityListUri, String authString, Date lastUpdate)
      throws CantDetermineDataSetException, IOException, CantRetrieveFileException {
    RemoteFilesList remoteFilesList = resourceSyncFileLoader.getRemoteFilesList(capabilityListUri, authString);

    List<RemoteFile> resources = new ArrayList<>();

    if (!remoteFilesList.getChangeList().isEmpty()) {
      if (lastUpdate != null) {
        for (RemoteFile remoteFile : remoteFilesList.getChangeList()) {
          if (remoteFile.getMetadata().getDateTime().after(lastUpdate)) {
            resources.add(remoteFile);
          }
        }
      } else {
        resources.addAll(remoteFilesList.getChangeList());
      }
      return resources;
    }

    for (RemoteFile remoteFile : remoteFilesList.getResourceList()) {
      if (remoteFile.getMetadata().isDataset()) {
        resources.add(remoteFile);
        return resources;
      }
    }

    if (remoteFilesList.getResourceList().size() == 1) {
      resources.add(remoteFilesList.getResourceList().get(0));
    } else {
      throw new CantDetermineDataSetException();
    }

    return resources;
  }

  private List<RemoteFile> filter(String capabilityListUri, String userSpecifiedDataSet, String authString)
      throws CantRetrieveFileException {
    try {
      RemoteFilesList remoteFilesList = resourceSyncFileLoader.getRemoteFilesList(capabilityListUri, authString);

      List<RemoteFile> resources = new ArrayList<>();
      for (RemoteFile remoteFile : remoteFilesList.getResourceList()) {
        if (remoteFile.getUrl().equals(userSpecifiedDataSet)) {
          resources.add(remoteFile);
          break;
        }
      }

      return resources;
    } catch (IOException e) {
      return Collections.emptyList();
    }
  }

  @FunctionalInterface
  public interface WithFile {
    Future<?> withFile(InputStream inputStream, String url, String mediaType, Date dateTime) throws Exception;
  }
}
