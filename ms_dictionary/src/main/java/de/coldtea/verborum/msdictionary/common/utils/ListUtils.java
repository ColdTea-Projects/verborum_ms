package de.coldtea.verborum.msdictionary.common.utils;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ListUtils {

    public final <T, R> List<R> map(List<T> list, Function<? super T, ? extends R> mapper) {
        return list.stream()
                .map(mapper)
                .collect(Collectors.toList());
    }

    public final <T, R> List<R> flatMap(List<T> list, Function<? super T, ? extends Stream<R>> mapper) {
        return list.stream()
                .flatMap(mapper)
                .collect(Collectors.toList());
    }
}
