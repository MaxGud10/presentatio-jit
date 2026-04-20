package bench;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;


@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(3)
public class MegamorphicBenchmark
{

    private static final int SIZE = 500;

    private HashMap<Keys.IntKey,    Integer> intMap;
    private HashMap<Keys.LongKey,   Integer> longMap;
    private HashMap<Keys.PairKey,   Integer> pairMap;
    private HashMap<Keys.StringKey, Integer> stringMap;

    private Keys.IntKey[]    intKeys; // массивы ключей — создаём заранее
    private Keys.LongKey[]   longKeys;
    private Keys.PairKey[]   pairKeys;
    private Keys.StringKey[] stringKeys;

    private Object[]  shuffledKeys;   // перемешанный массив — для сценария A
    private int[]     shuffledHashes; // предвычисленные хеши — для сценария C
    private int[]     shuffledType;

    @Setup
    public void setup()
    {
        intMap    = new HashMap<>(SIZE * 2);
        longMap   = new HashMap<>(SIZE * 2);
        pairMap   = new HashMap<>(SIZE * 2);
        stringMap = new HashMap<>(SIZE * 2);

        intKeys    = new Keys.IntKey[SIZE];
        longKeys   = new Keys.LongKey[SIZE];
        pairKeys   = new Keys.PairKey[SIZE];
        stringKeys = new Keys.StringKey[SIZE];

        for (int i = 0; i < SIZE; i++)
        {
            intKeys[i]    = new Keys.IntKey   (i);
            longKeys[i]   = new Keys.LongKey  (i * 100_000L);
            pairKeys[i]   = new Keys.PairKey  (i, i * 7);
            stringKeys[i] = new Keys.StringKey("k" + i);  // строка создаётся один раз здесь

            intMap   .put(intKeys   [i], i);
            longMap  .put(longKeys  [i], i);
            pairMap  .put(pairKeys  [i], i);
            stringMap.put(stringKeys[i], i);
        }

        // строим перемешанный список из всех 4 * SIZE ключей
        List<int[]> indices = new ArrayList<>(SIZE * 4);
        for (int i = 0; i < SIZE; i++)
        {
            indices.add(new int[]{0, i});
            indices.add(new int[]{1, i});
            indices.add(new int[]{2, i});
            indices.add(new int[]{3, i});
        }

        Collections.shuffle(indices);

        int total      = indices.size();
        shuffledKeys   = new Object[total];
        shuffledHashes = new int[total];
        shuffledType   = new int[total];

        for (int i = 0; i < total; i++)
            {
            int type = indices.get(i)[0];
            int idx  = indices.get(i)[1];
            shuffledType[i] = type;
            switch (type)
            {
                case 0 -> { shuffledKeys[i] = intKeys   [idx]; shuffledHashes[i] = intKeys[idx]   .hashCode(); }
                case 1 -> { shuffledKeys[i] = longKeys  [idx]; shuffledHashes[i] = longKeys[idx]  .hashCode(); }
                case 2 -> { shuffledKeys[i] = pairKeys  [idx]; shuffledHashes[i] = pairKeys[idx]  .hashCode(); }
                case 3 -> { shuffledKeys[i] = stringKeys[idx]; shuffledHashes[i] = stringKeys[idx].hashCode(); }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // сценарий A: megamorphic — случайный порядок типов
    // JIT видит непредсказуемое чередование 4 типов -> не инлайнит
    // ─────────────────────────────────────────────────────────────────
    @Benchmark
    public int scenarioA_megamorphic()
    {
        int sum = 0;
        int n = shuffledKeys.length;
        for (int i = 0; i < n; i++)
        {
            sum += doLookup(shuffledType[i], shuffledKeys[i]);
        }

        return sum;
    }

    private int doLookup(int type, Object key)
    {
        Integer v = switch (type)
        {
            case 0 -> intMap .get((Keys.IntKey)  key);
            case 1 -> longMap.get((Keys.LongKey) key);
            case 2 -> pairMap.get((Keys.PairKey) key);

            default -> stringMap.get((Keys.StringKey) key);
        };
        return v != null ? v : 0;
    }

    // ─────────────────────────────────────────────────────────────────
    // сценарий B: monomorphic — каждый тип в своём методе
    // JIT видит один тип -> инлайнит hashCode() -> быстрее
    // ─────────────────────────────────────────────────────────────────
    @Benchmark
    public int scenarioB_monomorphic()
    {
        int sum = 0;
        for (int i = 0; i < SIZE; i++)
        {
            sum += lookupInt(i);
            sum += lookupLong(i);
            sum += lookupPair(i);
            sum += lookupString(i);
        }

        return sum;
    }

    private int lookupInt(int i)
    {
        Integer v = intMap.get(intKeys[i]);
        return v != null ? v : 0;
    }

    private int lookupLong(int i)
    {
        Integer v = longMap.get(longKeys[i]);
        return v != null ? v : 0;
    }

    private int lookupPair(int i)
    {
        Integer v = pairMap.get(pairKeys[i]);
        return v != null ? v : 0;
    }

    private int lookupString(int i)
    {
        Integer v = stringMap.get(stringKeys[i]);
        return v != null ? v : 0;
    }

    // ─────────────────────────────────────────────────────────────────
    // сценарий C: верхняя граница — хеш берём из массива
    // никакого вызова hashCode() вообще
    // ─────────────────────────────────────────────────────────────────
    @Benchmark
    public int scenarioC_precomputedHash()
    {
        int sum = 0;
        int n   = shuffledKeys.length;
        for (int i = 0; i < n; i++)
        {
            sum += shuffledHashes[i];
            sum += doLookup(shuffledType[i], shuffledKeys[i]);
        }

        return sum;
    }

    public static void main(String[] args) throws Exception
    {
        Options opt = new OptionsBuilder()
                .include(MegamorphicBenchmark.class.getSimpleName())
                .forks(1).warmupIterations(3).measurementIterations(5)
                .build();
        new Runner(opt).run();
    }
}