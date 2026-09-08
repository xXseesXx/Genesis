package genesis.core.fields;

/** Implementations are pure functions closed over an immutable seed and Params. */
public interface Field<T> {
    T sample(long x, long z);
}
