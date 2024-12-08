import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;

public class ExpresionesAritmeticasASM {

    // Contador global para generar nombres de temporales (T1, T2, etc.)
    private static int temporalCounter = 1;

    public static void main(String[] args) {
        String input;
        try ( // Crear un scanner para leer la entrada del usuario
                Scanner scanner = new Scanner(System.in)) {
            System.out.println("Ingrese la expresión aritmética: ");
            input = scanner.nextLine();
        }

        // Limpiar la expresión de espacios innecesarios
        input = input.replaceAll("\\s+", "");

        // Lista para almacenar los temporales generados
        List<String> temporales = new ArrayList<>();
        List<String> instruccionesASM = new ArrayList<>();

        // Procesar la expresión ingresada
        String resultado = procesarExpresion(input, temporales, instruccionesASM);

        // Imprimir todos los temporales generados
        for (String temporal : temporales) {
            System.out.println(temporal);
        }
        System.out.println("Resultado final: " + resultado);

        // Generar el archivo .ASM
        generarArchivoASM(instruccionesASM);
    }

    /**
     * Procesa una expresión aritmética para resolverla y generar temporales.
     *
     * @param expresion        La expresión aritmética a resolver.
     * @param temporales       Lista para almacenar los temporales generados.
     * @param instruccionesASM Lista para almacenar las instrucciones en
     *                         ensamblador.
     * @return El nombre del último temporal generado que representa el
     *         resultado de la expresión.
     */
    private static String procesarExpresion(String expresion, List<String> temporales, List<String> instruccionesASM) {
        // Resolver primero las expresiones contenidas dentro de paréntesis
        Pattern parentesisPattern = Pattern.compile("\\(([^()]+)\\)");
        Matcher matcher;

        // Buscar y resolver todas las subexpresiones dentro de paréntesis
        while ((matcher = parentesisPattern.matcher(expresion)).find()) {
            // Extraer el contenido dentro de los paréntesis
            String subExpresion = matcher.group(1);

            // Resolver la subexpresión recursivamente
            String temporal = procesarExpresion(subExpresion, temporales, instruccionesASM);

            // Reemplazar el paréntesis completo con el temporal generado
            expresion = expresion.replaceFirst(Pattern.quote(matcher.group(0)), temporal);
        }

        // Definir los operadores en orden de precedencia
        String[] operadores = { "\\*", "/", "\\+", "-", "=" };
        // Nombres de las operaciones correspondientes para los temporales
        String[] nombresOperadores = { "MUL", "DIV", "ADD", "SUB", "MOV" };

        // Procesar cada operador en el orden de precedencia
        for (int i = 0; i < operadores.length; i++) {
            // Expresión regular para encontrar operaciones con el operador actual
            Pattern operacionPattern = Pattern.compile("([a-zA-Z0-9.]+)" + operadores[i] + "([a-zA-Z0-9.]+)");

            // Buscar y resolver las operaciones
            while ((matcher = operacionPattern.matcher(expresion)).find()) {
                // Extraer los operandos de la operación
                String operando1 = matcher.group(1);
                String operando2 = matcher.group(2);

                // Generar un nombre para el nuevo temporal
                String temporal = "T" + temporalCounter++;

                // Crear la representación textual del temporal y su operación
                String operacion = String.format("%s -> %s, %s, %s", temporal, operando1, operando2,
                        nombresOperadores[i]);

                // Agregar la operación a la lista de temporales
                temporales.add(operacion);

                // Agregar la instrucción en ensamblador al archivo .ASM
                String instruccionASM = String.format("%s %s, %s", nombresOperadores[i], operando1, operando2);
                instruccionesASM.add(instruccionASM);

                // Reemplazar la operación en la expresión con el nombre del temporal
                expresion = expresion.replaceFirst(Pattern.quote(matcher.group(0)), temporal);
            }
        }

        // Devolver el resultado final de la expresión (último temporal generado)
        return expresion;
    }

    /**
     * Genera un archivo .ASM listo para compilar en emu8086.
     *
     * @param instruccionesASM Lista de instrucciones en ensamblador.
     */
    private static void generarArchivoASM(List<String> instruccionesASM) {
        try (FileWriter writer = new FileWriter("resultado.asm")) {
            // Escribir la cabecera básica del archivo ASM
            writer.write(".model small\n");
            writer.write(".stack 100h\n");
            writer.write(".data\n");
            writer.write(".code\n");
            writer.write("start:\n");

            // Escribir las instrucciones generadas
            for (String instruccion : instruccionesASM) {
                writer.write("    " + instruccion + "\n");
            }

            // Finalizar el programa
            writer.write("    mov ah, 4Ch\n");
            writer.write("    int 21h\n");
            writer.write("end start\n");

            System.out.println("Archivo ASM generado exitosamente: resultado.asm");
        } catch (IOException e) {
            System.err.println("Error al generar el archivo ASM: " + e.getMessage());
        }
    }
}
