package M2;
// copilot: disable
// @ts-nocheck

public class Scenario3 extends BaseClass {
    private static Integer[] array1 = {42, -17, 89, -256, 1024, -4096, 50000, -123456};
    private static Double[] array2 = {3.14159265358979, -2.718281828459, 1.61803398875, -0.5772156649, 0.0000001, -1000000.0};
    private static Float[] array3 = {1.1f, -2.2f, 3.3f, -4.4f, 5.5f, -6.6f, 7.7f, -8.8f};
    private static String[] array4 = {"123", "-456", "789.01", "-234.56", "0.00001", "-99999999"};
    private static Object[] array5 = {-1, 1, 2.0f, -2.0d, "3", "-3.0"};
    private static void bePositive(Object[] arr, int arrayNumber) {
        // Only make edits between the designated "Start" and "End" comments
        printScenario3ArrayInfo(arr, arrayNumber);
        // This should be solved without Copilot auto-completion, to toggle it, click the Copilot chat bubble at the top of the editor.
        //  Configure inline suggestions to "Disabled Inline Suggestions" (or similar) when writing code for this problem.

        // Challenge 1: Make each value positive
        // Challenge 2: Convert the values back to their original data type and assign it to the proper slot in the `output` array
        // Step 1: sketch out plan using comments (include ucid and date)
        // Step 2: Add/commit your outline of comments (required for full credit)
        // Step 3: Add code to solve the problem (add/commit as needed)
        Object[] output = new Object[arr.length];
        // Start Solution Edits

        // Like with the other problems, for the first challenge we simply have to iterate through the array and then perform an action to each of the constiutent parts of the array.
        // A simple way to do this is to use the Math.abs() method for each number, and then spit it out into the output array.
        // In order to populate the output array, we have to define its length as long as the inputted array, which is already done. 
        // We then go through each index of the array and then set our variable value equal to it.

        for (int i = 0; i < arr.length; i++) {
            if (arr[i] instanceof Integer) {
                output[i] = Math.abs((Integer) arr[i]);
            }
            else if (arr[i] instanceof Double) {
                output[i] = Math.abs((Double) arr[i]);
            }
            else if (arr[i] instanceof Float) {
                output[i] = Math.abs((Float) arr[i]);
            }
            else if (arr[i] instanceof String)
            {
                // For this one, I'm a little lost. I don't know how to exactly go about it.
                // This is a bit of a hacky fix that doesn't actually address the fact that some of the numbers are integers.
                output[i] = Math.abs(Double.parseDouble((String) arr[i]));
            }


        }

        // End Solution Edits
        printOutputWithType(output, true);
    }

    public static void main(String[] args) {
        final String ucid = "si329"; // <-- change to your UCID
        // no edits below this line
        printHeader(ucid, 3);
        bePositive(array1, 1);
        bePositive(array2, 2);
        bePositive(array3, 3);
        bePositive(array4, 4);
        bePositive(array5, 5);
        printFooter(ucid, 3);

    }
}
