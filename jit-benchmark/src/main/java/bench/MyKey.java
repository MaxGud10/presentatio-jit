package bench;

/**
 * Кастомный ключ для HashMap.
 * hashCode() намеренно простой — чтобы было легко видеть
 * в JIT-логах инлайнится ли он или нет.
 */
public final class MyKey
{
    private final int id;

    public MyKey(int id)
    {
        this.id = id;
    }

    @Override
    public int hashCode()
    {
        return id * 31;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)             return true;
        if (!(o instanceof MyKey)) return false;
        
        return this.id == ((MyKey) o).id;
    }
}