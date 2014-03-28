package jsat.regression.evaluation;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Edward Raff
 */
public class MeanAbsoluteErrorTest
{
    
    public MeanAbsoluteErrorTest()
    {
    }
    
    @BeforeClass
    public static void setUpClass()
    {
    }
    
    @AfterClass
    public static void tearDownClass()
    {
    }
    
    @Before
    public void setUp()
    {
    }
    
    @After
    public void tearDown()
    {
    }

    /**
     * Test of getScore method, of class MeanAbsoluteError.
     */
    @Test
    public void testGetScore()
    {
        System.out.println("getScore");
        MeanAbsoluteError scorer = new MeanAbsoluteError();
        
        double[] pred = new double[]
        {
            0, 2, 4, 6, 8, 9
        };
        
        double[] truth = new double[]
        {
            0.5, 2, 3, 1, 8.5, 10
        };
        
        scorer.prepare();
        for(int i = 0; i < pred.length; i++)
            scorer.addResult(pred[i], truth[i], 1);
        assertEquals((0.5+1+5+0.5+1)/6, scorer.getScore(), 1e-4);
    }
    
}
