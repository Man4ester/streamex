/*
 * Copyright 2015 Tagir Valeev
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package javax.util.streamex;

import java.util.List;
import java.util.Spliterator;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * @author Tagir Valeev
 *
 */
/* package */final class PairPermutationSpliterator<T, R> implements Spliterator<R>, Cloneable {
    private long cur;
    private long limit;
    private final int size;
    private int idx1;
    private int idx2;
    private final List<T> list;
    private final BiFunction<? super T, ? super T, ? extends R> mapper;

    public PairPermutationSpliterator(List<T> list, BiFunction<? super T, ? super T, ? extends R> mapper) {
        this.list = list;
        this.size = list.size();
        this.idx2 = 1;
        this.limit = size * (size - 1L) / 2;
        this.mapper = mapper;
    }

    @Override
    public long estimateSize() {
        return limit - cur;
    }

    @Override
    public int characteristics() {
        return Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED;
    }

    /*
     * Calculates (int) (Math.sqrt(8 * n + 1)-1)/2 using the Newton Method
     * Produces exact result for any long input from 0 to 0x1FFFFFFFC0000000L
     * (2^61-2^30). Math.sqrt cannot be used here due to the low precision
     */
    static int isqrt(long n) {
        // Good starting guess which is the lowest possible 2^k-1 number
        // bigger than the result
        long xi = (1 << (32 - (Long.numberOfLeadingZeros(n) - 1) / 2)) - 1;
        // Usually no more than 5 iterations is necessary
        while (true) {
            long xi1 = (xi * xi + 2 * n) / (2 * xi + 1);
            if (xi1 == xi) {
                return (int) xi;
            }
            xi = xi1;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Spliterator<R> trySplit() {
        long size = limit - cur;
        if (size >= 2) {
            PairPermutationSpliterator<T, R> clone;
            try {
                clone = (PairPermutationSpliterator<T, R>) clone();
            } catch (CloneNotSupportedException e) {
                throw new InternalError();
            }
            clone.limit = this.cur = this.cur + size / 2;
            int s = this.size;
            long rev = s * (s - 1L) / 2 - this.cur - 1;
            int row = isqrt(rev);
            int col = (int) (rev - (row) * (row + 1L) / 2);
            this.idx1 = s - row - 2;
            this.idx2 = s - col - 1;
            return clone;
        }
        return null;
    }

    @Override
    public boolean tryAdvance(Consumer<? super R> action) {
        if (cur == limit)
            return false;
        action.accept(mapper.apply(list.get(idx1), list.get(idx2)));
        cur++;
        if (++idx2 == size) {
            idx2 = ++idx1 + 1;
        }
        return true;
    }

    @Override
    public void forEachRemaining(Consumer<? super R> action) {
        int idx1 = this.idx1;
        int idx2 = this.idx2;
        int size = this.size;
        long cur = this.cur;
        long limit = this.limit;
        while (cur < limit) {
            T item1 = list.get(idx1++);
            while (cur < limit && idx2 < size) {
                T item2 = list.get(idx2++);
                action.accept(mapper.apply(item1, item2));
                cur++;
            }
            idx2 = idx1 + 1;
        }
        this.cur = this.limit;
    }
}