package avrobase;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import org.apache.avro.specific.SpecificRecord;

/**
 * An astract indexing AvroBase forwarder. Updated indexes whenever data is created, mutated, or deleted.
 * Handles search() itself (does not pass request to the delegate).
 *
 * @author john
 */
public class IndexedAvroBase<T extends SpecificRecord, K, Q> extends ForwardingAvroBase<T, K> implements Searchable<T,K,Q> {
  protected final Index<T,K,Q> index;

  @Inject
  public IndexedAvroBase(final AvroBase<T, K> delegate, final Index<T, K, Q> index) {
    super(delegate);
    this.index = index;
  }

  @Override
  public K create(T value) throws AvroBaseException {
    final K row = delegate().create(value);
    index.index(new Row<T,K>(value, row));
    return row;
  }

  @Override
  public Row<T, K> mutate(K row, final Mutator<T> tMutator) throws AvroBaseException {
    final boolean[] success = {false};
    final Row<T, K> newRow = delegate().mutate(row, new Mutator<T>() {
      @Override
      public T mutate(T value) {
        T mutate = tMutator.mutate(value);
        if (mutate != null) {
          success[0] = true;
        }
        return mutate;
      }
    });
    if (success[0]) {
      index.index(newRow);
    }
    return newRow;
  }

  @Override
  public Row<T, K> mutate(K row, final Mutator<T> tMutator, Creator<T> tCreator) throws AvroBaseException {
    final boolean[] success = {false};
    final Row<T, K> newRow = delegate().mutate(row, new Mutator<T>() {
      @Override
      public T mutate(T value) {
        T mutate = tMutator.mutate(value);
        if (mutate != null) {
          success[0] = true;
        }
        return mutate;
      }
    }, tCreator);
    if (success[0]) {
      index.index(newRow);
    }
    return newRow;
  }

  @Override
  public void put(K row, T value) throws AvroBaseException {
    delegate().put(row, value);
    index.index(new Row<T,K>(value, row));
  }

  @Override
  public boolean put(K row, T value, long version) throws AvroBaseException {
    final boolean rv = delegate().put(row, value, version);
    if (rv) {
      index.index(new Row<T,K>(value, row));
    }
    return rv;
  }

  @Override
  public void delete(K row) throws AvroBaseException {
    delegate().delete(row);
    index.unindex(row);
  }

  public Iterable<Row<T, K>> search(Q query) throws AvroBaseException {
    return Iterables.transform(index.search(query), new Function<K, Row<T,K>>() {
      @Override
      public Row<T, K> apply(K from) {
        if (from == null) return null;
        return delegate().get(from);
      }
    });
  }

  @Override
  public Row<T, K> lookup(Q query) throws AvroBaseException {
    return Iterables.getFirst(search(query), null);
  }
}