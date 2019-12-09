import lombok.MateuMDDEntity;

import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Version;

@MateuMDDEntity
public class MateuMDDEntityTest {

  @Id@GeneratedValue(strategy = GenerationType.IDENTITY)
  private long id;

  public void setId(long id) {
    this.id = id;
  }

  public long getId() {
    return this.id;
  }

  @Version
  private int __version;

  public void set__version(int __version) {
    this.__version = __version;
  }

  public int get__version() {
    return this.__version;
  }

  private final String name;

  public String getName() {
    return this.name;
  }


  public String toString() {
    return "" + this.getName();
  }

  public boolean equals(final Object o) {
    if (o == this) return true;
    if (!(o instanceof MateuMDDEntityTest)) return false;
      final MateuMDDEntityTest other = (MateuMDDEntityTest)o;
      if (!other.canEqual((Object)this)) return false;
      if (this.id != other.id) return false;
      return true;

  }

  protected boolean canEqual(final Object other) {
    return other instanceof MateuMDDEntityTest;
  }


  public int hashCode() {
    return this.getClass().hashCode();
  }


  protected MateuMDDEntityTest() {
    this.name = null;
  }

  public MateuMDDEntityTest(String name) {
    this.name = name;
  }

    public void test() {
      System.out.println("Hola!");
    }


    public static void main(String[] args) {
        new MateuMDDEntityTest().test();
    }
}
