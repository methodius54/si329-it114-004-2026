package M2;
// copilot: disable
// @ts-nocheck

public class Scenario1 extends BaseClass {
    private static int[] array1 = {0,1,2,3,4,5,6,7,8,9};   
    private static int[] array2 = {9,8,7,6,5,4,3,2,1,0};
    private static int[] array3 = {0,0,1,1,2,2,3,3,4,4,5,5,6,6,7,7,8,8,9,9};
    private static int[] array4 = {9,9,8,8,7,7,6,6,5,5,4,4,3,3,2,2,1,1,0,0}; 
    private static void printOdds(int[] arr, int arrayNumber){
        // Only make edits between the designated "Start" and "End" comments
        printScenario1ArrayInfo(arr, arrayNumber);
        // This should be solved without Copilot auto-completion, to toggle it, click the Copilot chat bubble at the top of the editor.
        //  Configure inline suggestions to "Disabled Inline Suggestions" (or similar) when writing code for this problem.

        // Challenge 1: From each passed in array, print odd values only in a single line separated by commas and a space after each comma (should not have leading or trailing commas)
        // Step 1: sketch out plan using comments (include ucid and date)
        // Step 2: Add/commit your outline of comments (required for full credit)
        // Step 3: Add code to solve the problem (add/commit as needed)
        // Start Solution Edits

        // si329 02-23-2026
        // What we are attempting to achieve here is to basically iterate through the existing array. 
        // Then, we need to output the odd numbers in the array that we have compiled into possibly its own separate array.
        // The provided function takes in the array and stores the specific index of the array.

        // A possible way of going about this is creating an array of odd numbers to check against. However, this is more tedious and unnecessarily so.
        // We can instead use a modulus operator to check if the number is odd. If the number modulus of 2 is not equal to 0, we can inductively conclude that it is odd.
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] % 2 != 0) {
                System.out.print(arr[i] + ", ");
            }
        }

        // End Solution Edits
        System.out.println("");
        System.out.println("______________________________________");
    }
    public static void main(String[] args) {
        final String ucid = "si329"; // <-- change to your UCID
        // no edits below this line
        printHeader(ucid, 1);
        printOdds(array1,1);
        printOdds(array2,2);
        printOdds(array3,3);
        printOdds(array4,4);
        printFooter(ucid, 1);
        
    }
}
