package bench;

public class Keys
{
    public static final class IntKey
    {
        private final int id;
        public IntKey(int id) { this.id = id; }

        @Override public int hashCode() { return id * 31; }
        @Override public boolean equals(Object o)
        {
            return o instanceof IntKey && ((IntKey) o).id == this.id;
        }
    }

    /** Ключ на основе long — чуть сложнее */
    public static final class LongKey
    {
        private final long id;
        public LongKey(long id) { this.id = id; }

        @Override public int hashCode()
        {
            return (int)(id ^ (id >>> 32));
        }

        @Override public boolean equals(Object o)
        {
            return o instanceof LongKey && ((LongKey) o).id == this.id;
        }
    }

    /** Ключ на основе двух int — хеш через XOR */
    public static final class PairKey
    {
        private final int a, b;
        public PairKey(int a, int b) { this.a = a; this.b = b; }

        @Override public int hashCode() { return a * 1000003 ^ b; }
        @Override public boolean equals(Object o)
        {
            return o instanceof PairKey && ((PairKey) o).a == this.a && ((PairKey) o).b == this.b;
        }
    }

    /** Ключ на основе String — делегирует в String.hashCode() */
    public static final class StringKey
    {
        private final String value;
        public StringKey(String value) { this.value = value; }

        @Override public int hashCode() { return value.hashCode() * 17; }
        @Override public boolean equals(Object o)
        {
            return o instanceof StringKey && ((StringKey) o).value.equals(this.value);
        }
    }
}