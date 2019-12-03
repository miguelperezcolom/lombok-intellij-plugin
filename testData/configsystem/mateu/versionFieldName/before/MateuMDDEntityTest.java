import lombok.MateuMDDEntity;

@MateuMDDEntity
public class MateuMDDEntityTest {

    private final String name;

    public void test() {
      System.out.println("Hola!");
    }

    public static void main(String[] args) {
        new MateuMDDEntityTest().test();
    }
}
