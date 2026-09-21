package com.brainrotnotif.data;

import java.util.List;

public interface UsageEventReader {

    /**
     * События в полуинтервале [fromMs, toMs), отсортированные по времени.
     * Контракт полуинтервала обязателен: вызывающая сторона использует toMs
     * предыдущего вызова как fromMs следующего и полагается на отсутствие
     * пересечений.
     */
    List<AppEvent> read(long fromMs, long toMs);
}
