package nl.knaw.huygens.timbuctoo.remote.rs.discover;

@FunctionalInterface
interface ApplyException<T, R, E extends Exception> {
    R apply(T val) throws E;
}
