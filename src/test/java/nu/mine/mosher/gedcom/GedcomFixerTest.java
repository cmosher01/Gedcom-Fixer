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
public class GedcomFixerTest {
    @Parameters(name = "DATE {0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {"01 JAN 1900", "01 JAN 1900"},
            {"1 JAN 1900", "01 JAN 1900"},
            {"01 jan 1900", "01 JAN 1900"},
            {"3 jul 1966", "03 JUL 1966"},
            {"apr 1968", "APR 1968"},
            {"1974", "1974"},
            {"17 April 1899","17 APR 1899"},
            {"April 1899","APR 1899"},

            {"ABT 1 apr 1659", "ABT 01 APR 1659"},
            {"ABT apr 1659", "ABT APR 1659"},
            {"ABT 1659", "ABT 1659"},
            {"BEF 1 apr 1659", "BEF 01 APR 1659"},
            {"AFT 1 apr 1659", "AFT 01 APR 1659"},
            {"BET 1 apr 1659 AND 3 apr 1659", "BET 01 APR 1659 AND 03 APR 1659"},
            {"FROM 1 apr 1659", "FROM 01 APR 1659"},
            {"TO 1 apr 1659", "TO 01 APR 1659"},
            {"FROM 1 apr 1659 TO 3 apr 1659", "FROM 01 APR 1659 TO 03 APR 1659"},

            {"10/25/2013", "25 OCT 2013"},
            {"25/10/2013", "25 OCT 2013"},
            {"2013/10/25", "25 OCT 2013"},
            {"1935-1993", "FROM 1935 TO 1993"},

            {"Abt. 01 Apr 1659", "ABT 01 APR 1659"},
            {"abt 01 Apr 1659", "ABT 01 APR 1659"},
            {"c 01 Apr 1659", "ABT 01 APR 1659"},

            {"Bet. 1 Sep 1981-2 Jun 1984", "BET 01 SEP 1981 AND 02 JUN 1984"},
            {"Bet. 1 Sep 1981–2 Jun 1984", "BET 01 SEP 1981 AND 02 JUN 1984"},
            {"Bet. Sep 1981–Jun 1984", "BET SEP 1981 AND JUN 1984"},
            {"Bet. 1981–1984", "BET 1981 AND 1984"},
            {"Bet. 1–2 Jun 1984", "BET 01 JUN 1984 AND 02 JUN 1984"},
            {"Bet. Jan–Jun 1984", "BET JAN 1984 AND JUN 1984"},
            {"Bet. 1 Jan–2 Jun 1984", "BET 01 JAN 1984 AND 02 JUN 1984"},
            {"Bet. Jun–29 Aug 1987", "BET JUN 1987 AND 29 AUG 1987"},
            {"Bet. 2 Dec 1984–May 1985", "BET 02 DEC 1984 AND MAY 1985"},
            {"Bet. 9 Feb 1894–1897", "BET 09 FEB 1894 AND 1897"},
            {"Bet. 1 Jun–Oct 1710", "BET 01 JUN 1710 AND OCT 1710"},
            {"Bet. 1682–2 Apr 1700", "BET 1682 AND 02 APR 1700"},
            {"Bet. 1 Sep 1981–1984", "BET 01 SEP 1981 AND 1984"},
            {"Bet. 1 Sep 1657-", "AFT 01 SEP 1657"},
            {"Aft. 1 Sep 1695", "AFT 01 SEP 1695"},
            {"after 1 Sep 1695", "AFT 01 SEP 1695"},
            {"Bet. –1 Sep 1657", "BEF 01 SEP 1657"},
            {"Bef. 1 Sep 1695", "BEF 01 SEP 1695"},
            {"before 1 Sep 1695", "BEF 01 SEP 1695"},

            {"@@#DJULIAN@@ 1 JAN 1688", "01 JAN 1688"},
            {"@#DJULIAN@ 1 JAN 1688", "01 JAN 1688"},
            {"@@@@#DJULIAN@@@@ 1 JAN 1688", "01 JAN 1688"},

            {"1 XXX 1900", "1900"},
            {"XXX 1900", "1900"},
        });
    }

    private final String input;
    private final String expected;


    public GedcomFixerTest(final String input, final String expected) {
        this.input = input;
        this.expected = expected;
    }

    @Test
    public void test() {
        assertThat(GedcomFixer.fixDate(this.input), equalTo(this.expected));
    }
}
