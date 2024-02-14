package de.coldtea.verborum.msdictionary.common.utils;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ListUtils {

    public final <T, R> List<R> map(List<T> list, Function<? super T, ? extends R> mapper) {
        return list.stream()
                .map(mapper)
                .collect(Collectors.toList());
    }
}
