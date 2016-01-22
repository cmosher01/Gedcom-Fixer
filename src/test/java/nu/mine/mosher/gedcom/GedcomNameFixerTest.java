package nu.mine.mosher.gedcom;

import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

/**
 * Created by user on 1/16/16.
 */
@RunWith(Parameterized.class)
public class GedcomNameFixerTest {
    @Parameters(name = "DATE {0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"John /Smith/", "John /Smith/"},
                {"John  /Smith/", "John /Smith/"},
                {"John/Smith/", "John /Smith/"},
                {"John /Smith/ ", "John /Smith/"},
                {" John /Smith/", "John /Smith/"},
                {"John /Smith/ Jr", "John /Smith/ Jr"},
                {"John /Smith/Jr", "John /Smith/ Jr"},
                {"John/Smith/Jr", "John /Smith/ Jr"},
                {"John /Smith/, Jr", "John /Smith/, Jr"},
                {"John //", "John //"},
                {"John//", "John //"},
                {"John", "John //"},
                {"John Smith", "John Smith //"},
                {"John James /Smith/", "John James /Smith/"},
                {"   John   James   /Smith/   ", "John James /Smith/"},
                {"John /  Smith   Jones  /", "John /Smith Jones/"},
                {"John /Smith Jones/", "John /Smith Jones/"},
                {"John/Smith Jones/I", "John /Smith Jones/ I"},
        });
    }

    private final String input;
    private final String expected;


    public GedcomNameFixerTest(final String input, final String expected) {
        this.input = input;
        this.expected = expected;
    }

    @Test
    public void test() {
        assertThat(GedcomFixer.formatName(this.input), equalTo(this.expected));
    }
}
