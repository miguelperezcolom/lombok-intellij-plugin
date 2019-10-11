import lombok.MateuMDDEntity;

@MateuMDDEntity
public class MateuMDDEntityTest {

    private String name;

    public void test() {
      System.out.println("Hola!");
    }

    public static void main(String[] args) {
        new MateuMDDEntityTest().test();
    }
}
