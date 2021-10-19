package nl.knaw.huygens.timbuctoo.remote.rs.download.exceptions;

public class CantDetermineDataSetException extends Exception {
  public CantDetermineDataSetException() {
    super("Cannot determine dataset");
  }
}

