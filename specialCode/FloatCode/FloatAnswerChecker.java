public class FloatAnswerChecker {

    public static void main(String[] args) {
        // 假设这是题目给定的标准答案
        double inputAnswer = Double.parseDouble(args[0]);
        double outputAnswer = Double.parseDouble(args[1]);
        // 允许的误差范围
        double tolerance = 10;
        if (Math.abs(inputAnswer - outputAnswer) <= tolerance){
            System.out.println("OK, The answer is Correct");
        }else {
            System.out.println("WA, The answer is Incorrect");
        }
    }
}