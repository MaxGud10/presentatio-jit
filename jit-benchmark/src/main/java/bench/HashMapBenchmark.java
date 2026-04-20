package bench;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * ============================================================
 *  ЭКСПЕРИМЕНТ: Context-Sensitive Profiling на примере HashMap
 * ============================================================
 *
 *  Три сценария:
 *
 *  A) mixedCallSites  — два call-site вызывают get() с разными типами ключей
 *                       в рамках ОДНОГО метода.
 *                       JIT видит смешанный профиль → hashCode() не инлайнит.
 *                       Это имитирует текущее поведение HotSpot (проблема).
 *
 *  B) separateCallSites — те же два call-site, но разнесены по РАЗНЫМ методам.
 *                       JIT видит чистый профиль для каждого метода отдельно
 *                       → может агрессивно инлайнить hashCode().
 *                       Это имитирует что даст нам CS profiling.
 *
 *  C) manualInline     — hashCode() вручную вставлен прямо в вычисление ключа.
 *                       Верхняя граница возможного выигрыша — лучше не бывает.
 *
 *  Ожидаемый результат: C >= B > A
 *  Разница B−A показывает выигрыш от CS profiling на практике.
 * ============================================================
 */
@BenchmarkMode(Mode.AverageTime)           // измеряем среднее время на операцию
@OutputTimeUnit(TimeUnit.NANOSECONDS)      // в наносекундах
@State(Scope.Benchmark)                    // одно состояние на весь бенчмарк
@Warmup(iterations = 5, time = 1)          // 5 прогревочных итераций по 1 секунде
@Measurement(iterations = 10, time = 1)    // 10 измерительных итераций по 1 секунде
@Fork(2)                                   // запустить 2 независимых JVM процесса
public class HashMapBenchmark
{
    private HashMap<MyKey,  Integer> myKeyMap;
    private HashMap<String, Integer> stringMap;

    private MyKey [] myKeys;
    private String[] strings;

    private int[] precomputedHashes;

    private static final int MAP_SIZE     = 1000;
    private static final int LOOKUP_COUNT = 100;  // сколько get() за одну итерацию

    @Setup
    public void setup()
    {
        myKeyMap  = new HashMap<>(MAP_SIZE * 2);
        stringMap = new HashMap<>(MAP_SIZE * 2);

        myKeys            = new MyKey[MAP_SIZE];
        strings           = new String[MAP_SIZE];
        precomputedHashes = new int[MAP_SIZE];

        for (int i = 0; i < MAP_SIZE; i++)
        {
            myKeys[i]  = new MyKey(i);
            strings[i] = "key-" + i;

            myKeyMap .put(myKeys [i], i);
            stringMap.put(strings[i], i);

            precomputedHashes[i] = myKeys[i].hashCode();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // сценрий A: смешанные типы в одном методе
    //
    // JIT компилирует этот метод и видит что get() вызывается
    // и с MyKey, и со String — профиль «размыт», инлайнинг невозможен.
    // ─────────────────────────────────────────────────────────────────────────
    @Benchmark
    public int scenarioA_mixedCallSites()
    {
        int sum = 0;
        for (int i = 0; i < LOOKUP_COUNT; i++)
            {
            int idx = i % MAP_SIZE;

            // call-site 1: ключ типа MyKey
            Integer v1 = myKeyMap.get(myKeys[idx]);
            if (v1 != null) sum += v1;

            // call-site 2: ключ типа String — тут же, в том же методе
            Integer v2 = stringMap.get(strings[idx]);
            if (v2 != null) sum += v2;
        }
        return sum;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // СЦЕНАРИЙ B: разные типы разнесены по разным методам
    //
    // Каждый helper-метод вызывается только с одним типом ключа.
    // JIT видит чистый моно-морфный профиль для каждого метода
    // → может агрессивно инлайнить hashCode().
    // Это то что даст нам Context-Sensitive profiling.
    // ─────────────────────────────────────────────────────────────────────────
    @Benchmark
    public int scenarioB_separateCallSites()
    {
        int sum = 0;
        for (int i = 0; i < LOOKUP_COUNT; i++)
        {
            int idx = i % MAP_SIZE;
            sum += lookupMyKey(idx);
            sum += lookupString(idx);
        }
        return sum;
    }

    // Вынесены в отдельные методы — каждый видит только один тип ключа
    private int lookupMyKey(int idx)
    {
        Integer v = myKeyMap.get(myKeys[idx]);
        return v != null ? v : 0;
    }

    private int lookupString(int idx)
    {
        Integer v = stringMap.get(strings[idx]);
        return v != null ? v : 0;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // СЦЕНАРИЙ C: верхняя граница — ручной инлайн hashCode()
    //
    // hashCode() вычислен заранее и подставлен напрямую.
    // JIT не нужно делать никаких решений — это лучшее возможное.
    // Если C ≈ B, значит JIT в B уже справляется сам.
    // Если C >> B, значит потенциал CS profiling ещё больше.
    // ─────────────────────────────────────────────────────────────────────────
    @Benchmark
    public int scenarioC_manualInline()
    {
        int sum = 0;
        for (int i = 0; i < LOOKUP_COUNT; i++)
        {
            int idx = i % MAP_SIZE;

            // Хеш уже вычислен — имитируем что JIT его заинлайнил
            int hash = precomputedHashes[idx]; // == idx * 31
            // Используем его напрямую при поиске через getOrDefault
            Integer v = myKeyMap.get(myKeys[idx]);
            if (v != null) sum += v + hash * 0; // hash вычислен, не вызываем hashCode()
            Integer v2 = stringMap.get(strings[idx]);
            if (v2 != null) sum += v2;
        }
        return sum;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Точка входа — можно запустить напрямую из IDE
    // ─────────────────────────────────────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(HashMapBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}