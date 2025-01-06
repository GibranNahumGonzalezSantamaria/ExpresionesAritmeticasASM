import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.*;
import javax.swing.JFileChooser;
/**
 * Clase principal que procesa expresiones aritméticas desde un archivo .txt,
 * valida su estructura, y genera un archivo Ensamblador (ASM).
 */
public class ExpresionesAritmeticasASM {
    // ---------------------------------------------------------------------------------
    // PROPIEDADES
    // ---------------------------------------------------------------------------------
    private static int temporalCounter = 1;
    // Contador para variables temporales

    // ---------------------------------------------------------------------------------
    // MÉTODO PRINCIPAL
    // ---------------------------------------------------------------------------------
    @SuppressWarnings("ConvertToTryWithResources")
    public static void main(String[] args) {
        // Selector de archivos mediante un cuadro de diálogo
        while (true) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Seleccione un archivo .txt");

            // Abrir el diálogo para seleccionar el archivo .txt
            int result = fileChooser.showOpenDialog(null);
            if (result != JFileChooser.APPROVE_OPTION) {
                System.out.println("No se seleccionó ningún archivo.");
                return;
            }

            File selectedFile = fileChooser.getSelectedFile();
            if (!selectedFile.getName().endsWith(".txt")) {
                System.err.println("Por favor, seleccione un archivo con extensión .txt");
                continue;
            }

            // Leer el contenido del archivo y eliminar espacios
            String ExpresionAritmetica;
            try {
                String contenido = new String(Files.readAllBytes(Paths.get(selectedFile.getAbsolutePath())));
                ExpresionAritmetica = contenido.replaceAll("\\s+", "").toLowerCase(); // Convertir a minúsculas
            } catch (IOException e) {
                System.err.println("Error al leer el archivo: " + e.getMessage());
                return;
            }

            // Formatear la expresión para una mejor visualización
            String expresionFormateada = formatearExpresion(ExpresionAritmetica);

            // Eliminar los signos negativos de variables en la expresión
            ExpresionAritmetica = ExpresionAritmetica.replaceAll("\\(-([a-zA-Z_][a-zA-Z0-9_]*)\\)", "$1");

            // Mostrar la expresión original en la consola
            System.out.println("\nExpresión Aritmética Original: " + expresionFormateada + "\n");

            // Validar la estructura de la expresión
            if (!esExpresionValida(ExpresionAritmetica)) {
                System.err.println(
                        "La expresión es inválida (operadores consecutivos, paréntesis mal, etc.). Por favor, seleccione un nuevo archivo.");
                continue;
            }

            // Identificar la variable de asignación y las variables utilizadas en la expresión
            String variableIzquierda = identificarVariableIzquierda(ExpresionAritmetica);
            Set<String> variables = identificarVariables(ExpresionAritmetica);
            Set<String> variables_neg = identificarVariablesNegativas(expresionFormateada);
            variables.remove(variableIzquierda);

            // Solicitar al usuario los valores de las variables
            Scanner scanner = new Scanner(System.in);
            Map<String, Double> valoresVariables = obtenerValoresDeVariables(variables, variables_neg, scanner);
            scanner.close();

            // Reemplazar las variables en la expresión con sus nombres
            String input = ExpresionAritmetica;
            for (Map.Entry<String, Double> entry : valoresVariables.entrySet()) {
                input = input.replaceAll("\\b" + Pattern.quote(entry.getKey()) + "\\b", entry.getKey());
            }

            // Inicializar listas para temporales e instrucciones ASM
            List<String> temporales = new ArrayList<>();
            List<String> instruccionesASM = new ArrayList<>();

            // Procesar la expresión y generar instrucciones ASM
            procesarExpresion(input, temporales, instruccionesASM, valoresVariables);

            // Mostrar en consola las operaciones intermedias
            System.out.println();
            for (String temp : temporales) {
                System.out.println(temp);
            }

            // Obtener y mostrar el resultado calculado
            double resultadoNumerico = valoresVariables.get(variableIzquierda);
            System.out.println("\n - Resultado: " + variableIzquierda + " = "
                    + String.format(Locale.US, "%.3f", resultadoNumerico) + "\n");

            // Formatear el resultado para ASM
            String resultadoFinalJava = (resultadoNumerico < 0)
                    ? "-" + String.format(Locale.US, "%.3f", Math.abs(resultadoNumerico))
                    : String.format(Locale.US, "%.3f", resultadoNumerico);

            // Verificar si el resultado es un valor numérico válido
            if (Double.isNaN(resultadoNumerico) || Double.isInfinite(resultadoNumerico)) {
                resultadoFinalJava = "000.000"; // Asignar 0.0 si no es un número válido
            }

            // Generar el archivo ASM con las instrucciones y variables procesadas
            try {
                generarArchivoASM(instruccionesASM, valoresVariables, variableIzquierda, variables_neg,
                        resultadoFinalJava,
                        expresionFormateada);
                System.out.println(" - Archivo ASM generado exitosamente: Resultado.ASM\n");
            } catch (IOException e) {
                System.err.println(" - Error al generar el archivo ASM: " + e.getMessage());
            }

            // Salir del bucle tras procesar correctamente la expresión
            break;
        }
        }
        /**
         * Formatea la expresión aritmética agregando espacios alrededor de los operadores
         * y asegurando una correcta visualización.
         * @param expresion La expresión aritmética sin espacios.
         * @return La expresión formateada con espacios adecuados.
         */
        private static String formatearExpresion(String expresion) {
            // Agregar espacios alrededor de los operadores
            expresion = expresion.replaceAll("(?<=[^\\s+\\-*/=])([+\\-*/=])(?=[^\\s+\\-*/=])", " $1 ");

            // Eliminar espacios en expresiones del tipo "z=( -a)" o "z=( -1)"
            expresion = expresion.replaceAll("\\(\\s*-\\s*", "(-");

            return expresion.trim();
        }
        // ---------------------------------------------------------------------------------
        // VALIDACIÓN DE LA EXPRESIÓN
        // ---------------------------------------------------------------------------------
        /**
         * Valida la estructura global de una expresión aritmética.
         * Verifica que cumpla con reglas sintácticas básicas.
         * @param expresion La expresión aritmética a validar.
         * @return true si la expresión es válida, false de lo contrario.
         */
        private static boolean esExpresionValida(String expresion) {
            // 1. No hay operadores consecutivos
            if (expresion.matches(".*[+\\-*/=]{2,}.*")) {
                return false;
            }

            // 2. Paréntesis correctamente precedidos por operadores
            if (expresion.matches(".*(?<![+\\-*/(=])\\(.*")) {
                return false;
            }

            // 3. No hay números seguidos de identificadores
            if (expresion.matches(".*\\d+[a-zA-Z_].*")) {
                return false;
            }

            // 4. Tiene exactamente un '='
            if (expresion.chars().filter(ch -> ch == '=').count() != 1) {
                return false;
            }

            // 5. Lado izquierdo válido y no aparece en el lado derecho
            String[] partes = expresion.split("=", 2);
            if (partes.length != 2) {
                return false;
            }
            String ladoIzquierdo = partes[0].trim();
            String ladoDerecho = partes[1].trim();
            if (!ladoIzquierdo.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                return false;
            }
            if (ladoDerecho.matches(".*\\b" + Pattern.quote(ladoIzquierdo) + "\\b.*")) {
                return false;
            }

            // 6. No hay caracteres inválidos
            if (expresion.matches(".*[^a-zA-Z0-9_+\\-*/=().].*")) {
                return false;
            }

            // 7. Paréntesis balanceados
            int contadorParentesis = 0;
            for (char c : expresion.toCharArray()) {
                if (c == '(')
                    contadorParentesis++;
                if (c == ')')
                    contadorParentesis--;
                if (contadorParentesis < 0) {
                    return false; // Más ')' que '('
                }
            }
            if (contadorParentesis != 0) {
                return false;
            }

            // 8. No hay variables internas como T1, T1_D, etc.
            if (expresion.matches(".*\\bT\\d+(_D)?\\b.*")) {
                return false;
            }

            // 9. No usar palabras reservadas
            String[] palabrasReservadas = { "ExpresionAritmetica", "Resultado", "Signo", "Enteros", "Punto", "Decimales",
                    "start" };
            for (String palabra : palabrasReservadas) {
                if (expresion.matches(".*\\b" + Pattern.quote(palabra.toLowerCase()) + "\\b.*")) {
                    return false;
                }
            }

            return true;
        }
        /**
         * Extrae la variable del lado izquierdo de la asignación en la expresión.
         * @param expresion La expresión aritmética completa.
         * @return El nombre de la variable a la izquierda del '='.
         */
        private static String identificarVariableIzquierda(String expresion) {
            int idx = expresion.indexOf('=');
            return (idx != -1) ? expresion.substring(0, idx) : null;
        }
        /**
         * Identifica todas las variables presentes en la expresión aritmética.
         * @param expresion La expresión aritmética a analizar.
         * @return Un conjunto de nombres de variables encontradas en la expresión.
         */
        private static Set<String> identificarVariables(String expresion) {
            Set<String> vars = new HashSet<>();
            Matcher m = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*").matcher(expresion);
            while (m.find()) {
                vars.add(m.group());
            }

            return vars;
        }
        /**
         * Identifica las variables que están precedidas por un signo negativo en la expresión.
         * @param expresion La expresión aritmética formateada.
         * @return Un conjunto de nombres de variables que tienen un signo negativo.
         */
        private static Set<String> identificarVariablesNegativas(String expresion) {
            Set<String> vars_neg = new HashSet<>();
            Matcher m = Pattern.compile("\\(-([a-zA-Z_][a-zA-Z0-9_]*)\\)").matcher(expresion);
            while (m.find()) {
                vars_neg.add(m.group(1)); // Agregar solo el nombre de la variable sin el signo negativo
            }

            return vars_neg;
        }
        // ---------------------------------------------------------------------------------
        // PEDIR VALORES PARA LAS VARIABLES DESDE CONSOLA
        // ---------------------------------------------------------------------------------
        /**
         * Solicita al usuario los valores numéricos para cada variable identificada en la expresión.
         * @param variables      Conjunto de variables positivas.
         * @param variables_neg  Conjunto de variables que tienen un signo negativo.
         * @param scanner        Objeto Scanner para leer la entrada del usuario.
         * @return Un mapa que asocia cada variable con su valor numérico correspondiente.
         */
        private static Map<String, Double> obtenerValoresDeVariables(Set<String> variables, Set<String> variables_neg,
                Scanner scanner) {
            Map<String, Double> map = new HashMap<>();

            // Procesar las variables que deben ser negativas
            for (String var : variables_neg) {
                while (true) {
                    System.out.print(" * Ingrese el valor de '" + var + "': ");
                    String entrada = scanner.next();
                    try {
                        double val = Double.parseDouble(entrada);
                        map.put(var, -val); // Asignar el valor negativo
                        break;
                    } catch (NumberFormatException ex) {
                        System.out.println("Error: Solo se permiten números.");
                    }
                }
            }

            // Procesar las variables que no tienen signo negativo
            for (String var : variables) {
                if (map.containsKey(var))
                    continue; // Omitir esta variable si ya fue asignada como negativa

                while (true) {
                    System.out.print(" * Ingrese el valor de '" + var + "': ");
                    String entrada = scanner.next();
                    try {
                        double val = Double.parseDouble(entrada);
                        map.put(var, val); // Asignar el valor positivo
                        break;
                    } catch (NumberFormatException ex) {
                        System.out.println("Error: Solo se permiten números.");
                    }
                }
            }

            return map;
        }
        // ---------------------------------------------------------------------------------
        // PROCESAR LA EXPRESIÓN Y GENERAR INSTRUCCIONES ASM
        // ---------------------------------------------------------------------------------
        /**
         * Procesa la expresión aritmética, descomponiéndola en operaciones básicas y
         * generando las instrucciones ASM correspondientes.
         * @param expresion        La expresión aritmética a procesar.
         * @param temporales       Lista para almacenar las operaciones temporales.
         * @param instruccionesASM Lista para almacenar las instrucciones ASM generadas.
         * @param valoresVariables Mapa que asocia variables con sus valores numéricos.
         * @return La representación final de la expresión procesada.
         */
        private static String procesarExpresion(
                String expresion,
                List<String> temporales,
                List<String> instruccionesASM,
                Map<String, Double> valoresVariables) {
            String[] operadoresJerarquia = { "\\(", "\\*", "/", "\\+", "-", "=" };
            String[] nombresOperadores = { "PAREN", "MUL", "DIV", "ADD", "SUB", "MOV" };

            // Procesar paréntesis de manera recursiva
            Pattern patParentesis = Pattern.compile("\\(([^()]+)\\)");
            Matcher matParen;
            while ((matParen = patParentesis.matcher(expresion)).find()) {
                String subExp = matParen.group(1);
                String temp = procesarExpresion(subExp, temporales, instruccionesASM, valoresVariables);
                expresion = expresion.replaceFirst(Pattern.quote("(" + subExp + ")"), temp);
            }

            // Procesar operadores según su jerarquía
            for (int i = 1; i < operadoresJerarquia.length; i++) {
                String regex = String.format(
                        "([a-zA-Z_][a-zA-Z0-9_]*|\\-?\\d+(\\.\\d+)?|T\\d+)%s([a-zA-Z_][a-zA-Z0-9_]*|\\-?\\d+(\\.\\d+)?|T\\d+)",
                        operadoresJerarquia[i]);

                Pattern patOp = Pattern.compile(regex);
                Matcher m;
                while ((m = patOp.matcher(expresion)).find()) {
                    String op1 = m.group(1);
                    String op2 = m.group(3);
                    String tempVar = "T" + (temporalCounter++);

                    if (nombresOperadores[i].equals("MOV")) {
                        double val2 = valoresVariables.containsKey(op2)
                                ? valoresVariables.get(op2)
                                : Double.parseDouble(op2);
                        valoresVariables.put(op1, val2);

                        String asm = generarInstruccionASM("MOV", op1, op2, tempVar);
                        instruccionesASM.add(asm);
                        return op1;
                    }

                    double v1 = valoresVariables.containsKey(op1)
                            ? valoresVariables.get(op1)
                            : Double.parseDouble(op1);
                    double v2 = valoresVariables.containsKey(op2)
                            ? valoresVariables.get(op2)
                            : Double.parseDouble(op2);

                    double r = calcularResultado(v1, v2, nombresOperadores[i]);
                    valoresVariables.put(tempVar, r);

                    // Almacenar la operación temporal para mostrarla
                    String operacion = String.format("    %s -> %s, %s, %s = %.3f", tempVar, op1, op2, nombresOperadores[i],
                            r);
                    temporales.add(operacion);

                    String asm = generarInstruccionASM(nombresOperadores[i], op1, op2, tempVar);
                    instruccionesASM.add(asm);

                    expresion = expresion.replaceFirst(Pattern.quote(m.group(0)), tempVar);
                }
            }

            return expresion;
        }
        /**
         * Realiza la operación aritmética entre dos operandos.
         * @param a  Primer operando.
         * @param b  Segundo operando.
         * @param op Operación a realizar (MUL, DIV, ADD, SUB).
         * @return El resultado de la operación.
         */
        private static double calcularResultado(double a, double b, String op) {
            return switch (op) {
                case "MUL" -> a * b;
                case "DIV" -> a / b;
                case "ADD" -> a + b;
                case "SUB" -> a - b;
                default -> throw new IllegalArgumentException("Operador no soportado: " + op);
            };
        }
        /**
         * Procesa un operando para su uso en instrucciones ASM.
         * @param operando El operando a procesar.
         * @return La representación procesada del operando.
         */
        private static String procesarOperando(String operando) {
            // Caso: Variable, temporal o identificador
            if (operando.matches("[a-zA-Z_][a-zA-Z0-9_]*|T\\d+")) {
                return operando + "_D"; // Agregar sufijo "_D"
            }

            if (operando.matches("\\-?\\d+\\.\\d+")) { // Número decimal negativo o positivo
                String[] partes = operando.split("\\.");
                return partes[1];
            }
            if (operando.matches("\\-?\\d+")) { // Número entero negativo o positivo
                return "000"; // Procesar como 000 o similar
            }

            // Por defecto, devolver el operando tal cual (nunca debería llegar aquí)
            return operando;
        }
        /**
         * Genera la instrucción ASM correspondiente a una operación aritmética.
         * @param operador El operador de la operación (MUL, DIV, ADD, SUB, MOV).
         * @param op1      Primer operando.
         * @param op2      Segundo operando.
         * @param tempVar  Variable temporal para almacenar el resultado.
         * @return La instrucción ASM generada como una cadena de texto.
         */
        private static String generarInstruccionASM(String operador, String op1, String op2, String tempVar) {
            StringBuilder sb = new StringBuilder();

            switch (operador) {
                case "MUL" -> {
                    sb.append("\n    ;Multiplicación\n")
                      .append(String.format("    MOV AX, %s\n", op1))
                      .append(String.format("    MOV BX, %s\n", op2))
                      .append("    CWD\n")
                      .append("    IMUL BX\n")
                      .append(String.format("    MOV %s, AX\n", tempVar))
                      .append("    ;Multiplicación_D\n")
                      .append(String.format("    MOV AX, %s\n", procesarOperando(op1)))
                      .append(String.format("    MOV BX, %s\n", procesarOperando(op2)))
                      .append("    CWD\n")
                      .append("    IMUL BX\n")
                      .append(String.format("    MOV %s_D, AX", tempVar));
                }
                case "DIV" -> {
                    sb.append("\n    ;División\n")
                      .append(String.format("    MOV AX, %s\n", op1))
                      .append("    XOR DX, DX\n")
                      .append(String.format("    MOV BX, %s\n", op2))
                      .append("    CWD\n")
                      .append("    IDIV BX\n")
                      .append(String.format("    MOV %s, AX\n", tempVar))
                      .append("    ;División_D\n")
                      .append(String.format("    MOV AX, %s\n", procesarOperando(op1)))
                      .append(String.format("    MOV BX, %s\n", procesarOperando(op2)))
                      .append("    CWD\n")
                      .append("    IDIV BX\n")
                      .append(String.format("    MOV %s_D, AX", tempVar));
                }
                case "ADD" -> {
                    sb.append("\n    ;Suma\n")
                      .append(String.format("    MOV AX, %s\n", op1))
                      .append(String.format("    ADD AX, %s\n", op2))
                      .append(String.format("    MOV %s, AX\n", tempVar))
                      .append("    ;Suma_D\n")
                      .append(String.format("    MOV AX, %s\n", procesarOperando(op1)))
                      .append(String.format("    ADD AX, %s\n", procesarOperando(op2)))
                      .append(String.format("    MOV %s_D, AX", tempVar));
                }
                case "SUB" -> {
                    sb.append("\n    ;Resta\n")
                      .append(String.format("    MOV AX, %s\n", op1))
                      .append(String.format("    SUB AX, %s\n", op2))
                      .append(String.format("    MOV %s, AX\n", tempVar))
                      .append("    ;Resta_D\n")
                      .append(String.format("    MOV AX, %s\n", procesarOperando(op1)))
                      .append(String.format("    SUB AX, %s\n", procesarOperando(op2)))
                      .append(String.format("    MOV %s_D, AX", tempVar));
                }
                case "MOV" -> {
                    sb.append("\n    ;Ajuste de decimales\n")
                      .append(String.format("    MOV AX, %s\n", procesarOperando(op2)))
                      .append("    CMP AX, 1000\n")
                      .append("    JL Ajuste_Menor\n\n")
                      .append("    ;Ajuste mayor\n")
                      .append("    SUB AX, 1000\n")
                      .append(String.format("    MOV %s, AX\n", procesarOperando(op2)))
                      .append(String.format("    MOV AX, %s\n", op2))
                      .append(String.format("    INC AX ;%s++\n", op2))
                      .append(String.format("    MOV %s, AX\n", op2))
                      .append("    JMP Fin_Ajuste\n\n")
                      .append("    Ajuste_Menor:\n")
                      .append(String.format("    CMP AX, 0\n"))
                      .append("    JGE Fin_Ajuste\n")
                      .append(String.format("    ADD AX, 1000\n"))
                      .append(String.format("    MOV %s, AX\n", procesarOperando(op2)))
                      .append(String.format("    MOV AX, %s\n", op2))
                      .append(String.format("    DEC AX ;%s--\n", op2))
                      .append(String.format("    MOV %s, AX\n", op2))
                      .append("\n    Fin_Ajuste:\n\n")
                      .append("    ;Asignación\n")
                      .append(String.format("    MOV AX, %s\n", op2))
                      .append(String.format("    MOV %s, AX\n", op1))
                      .append("    ;Asignación_D\n")
                      .append(String.format("    MOV AX, %s\n", procesarOperando(op2)))
                      .append(String.format("    MOV %s, AX", procesarOperando(op1)));
                }
                default -> throw new IllegalArgumentException("Operador no soportado: " + operador);
            }

            return sb.toString();
        }
        /**
         * Genera el archivo ASM con las secciones de datos y código, incluyendo las
         * instrucciones y variables necesarias.
         * @param instruccionesASM    Lista de instrucciones ASM generadas.
         * @param valoresVariables    Mapa que asocia variables con sus valores numéricos.
         * @param variableIzquierda  La variable que recibe el resultado de la expresión.
         * @param variables_neg       Conjunto de variables que tienen un signo negativo.
         * @param resultadoFinalJava  El resultado final formateado.
         * @param expresionFormateada La expresión aritmética original formateada.
         * @throws IOException Si ocurre un error al escribir el archivo.
         */
        private static void generarArchivoASM(
                List<String> instruccionesASM,
                Map<String, Double> valoresVariables,
                String variableIzquierda,
                Set<String> variables_neg,
                String resultadoFinalJava,
                String expresionFormateada) throws IOException {
            try (FileWriter writer = new FileWriter("Resultado.ASM")) {
                StringBuilder sb = new StringBuilder();

                // Separar las partes del resultado
                char Signo = resultadoFinalJava.charAt(0) == '-' ? '-' : '+';
                String[] partes = resultadoFinalJava.replace("-", "").split("\\.");
                String parteEntera = partes[0];
                String parteDecimal = partes.length > 1 ? partes[1] : "000";

                // 1) Encabezado del archivo ASM
                sb.append(".MODEL SMALL\n")
                  .append(".STACK 100h\n\n")
                  .append(".DATA\n");

                // 2) Declarar la variable principal y sus decimales
                if (variableIzquierda != null) {
                    sb.append("    ").append(variableIzquierda).append(" DW ?\n")
                      .append("    ").append(variableIzquierda).append("_D DW ?\n\n");
                }

                // Declarar las demás variables con sus valores
                for (Map.Entry<String, Double> entry : valoresVariables.entrySet()) {
                    String declaracion = convertirValorASM(entry.getKey(), entry.getValue(), variableIzquierda);
                    if (!declaracion.isEmpty()) {
                        sb.append("    ").append(declaracion);
                    }
                }

                sb.append("\n");

                // Declarar variables temporales
                for (int i = 1; i < temporalCounter; i++) {
                    sb.append("    T").append(i).append(" DW ?\n")
                      .append("    T").append(i).append("_D DW ?\n");
                }

                sb.append("\n    ExpresionAritmetica DB '").append(expresionFormateada)
                  .append("', 0Dh, 0Ah, 0Dh, 0Ah, '$'\n");

                // Declarar las variables con sus valores para imprimir (excluyendo temporales y variableIzquierda)
                for (Map.Entry<String, Double> entry : valoresVariables.entrySet()) {
                    String nombreVariable = entry.getKey();
                    if (!nombreVariable.equals(variableIzquierda) && !nombreVariable.startsWith("T")) {
                        double valor = entry.getValue();

                        // Verificar si la variable es negativa
                        if (variables_neg.contains(nombreVariable)) {
                            valor *= -1; // Cambiar el signo del valor
                        }

                        String valorFormateado = String.format(Locale.US, "%.3f", valor);
                        sb.append("    ").append(nombreVariable).append("_T DB '  ")
                          .append(nombreVariable).append(" = ").append(valorFormateado)
                          .append("', 0Dh, 0Ah, '$'\n");
                    }
                }

                sb.append("    Resultado DB 0Dh, 0Ah, '").append(variableIzquierda)
                  .append(" = ', '$'\n")
                  .append("    Signo DB ").append(convertirCadenaADecimal(String.valueOf(Signo)))
                  .append(", 5 DUP('$')\n")
                  .append("    Enteros DB ").append(convertirCadenaADecimal(parteEntera))
                  .append(", 5 DUP('$')\n")
                  .append("    Punto DB '.', '$'\n")
                  .append("    Decimales DB ").append(convertirCadenaADecimal(parteDecimal))
                  .append(", 5 DUP('$')\n\n");

                // 3) Segmento de código
                sb.append(".CODE\n")
                  .append("start:\n")
                  .append("    MOV AX, @DATA\n")
                  .append("    MOV DS, AX\n\n");

                // Instrucciones para imprimir la expresión aritmética
                sb.append("    ;Imprimir Expresión Aritmetica\n")
                  .append("    LEA DX, ExpresionAritmetica\n")
                  .append("    MOV AH, 09h\n")
                  .append("    INT 21h\n\n");

                // Instrucciones para imprimir los valores de las variables
                sb.append("    ;Imprimir variables\n");
                for (Map.Entry<String, Double> entry : valoresVariables.entrySet()) {
                    String nombreVariable = entry.getKey();
                    if (!nombreVariable.equals(variableIzquierda) && !nombreVariable.startsWith("T")) {
                        sb.append("    LEA DX, ").append(nombreVariable).append("_T\n")
                          .append("    MOV AH, 09h\n")
                          .append("    INT 21h\n");
                    }
                }

                // 4) Incluir las instrucciones ASM generadas
                sb.append("\n");
                for (String instruccion : instruccionesASM) {
                    // Reemplazar el punto por un punto y coma en los valores numéricos
                    String instruccionModificada = instruccion.replaceAll("(\\d+)\\.(\\d+)", "$1;$2");
                    sb.append("    ").append(instruccionModificada).append("\n");
                }

                // Imprimir el resultado desde las partes separadas
                sb.append("\n    ;Imprimir resultado\n")
                  .append("    LEA DX, Resultado\n")
                  .append("    MOV AH, 09h\n")
                  .append("    INT 21h\n\n");

                // Determinar el signo de la variable
                sb.append("    ;Determinar el signo de '").append(variableIzquierda).append("'\n")
                  .append("    MOV AX, ").append(variableIzquierda).append("\n")
                  .append("    CMP AX, 0\n")
                  .append("    JL Negativo\n")
                  .append("    MOV BYTE PTR ").append(variableIzquierda).append(", '+'\n")
                  .append("    JMP FIN_Signo\n")
                  .append("    Negativo:\n")
                  .append("    MOV BYTE PTR ").append(variableIzquierda).append(", '-'\n")
                  .append("    FIN_Signo:\n\n");

                // Imprimir el signo
                sb.append("    ;Imprimir signo\n")
                  .append("    LEA DX, Signo\n")
                  .append("    MOV AH, 09h\n")
                  .append("    INT 21h\n\n");

                // Conversión de la parte entera
                sb.append("    ;Conversión de '").append(variableIzquierda).append("' a texto (Enteros)\n")
                  .append("    MOV AX, ").append(variableIzquierda).append("\n")
                  .append("    MOV CX, 5\n")
                  .append("    LEA DI, ").append(variableIzquierda).append("\n")
                  .append("    MOV BX, 10\n\n")
                  .append("    LOOP_Enteros:\n")
                  .append("        XOR DX, DX\n")
                  .append("        DIV BX\n")
                  .append("        ADD DL, '0'\n")
                  .append("        DEC DI\n")
                  .append("        MOV [DI], DL\n")
                  .append("        DEC CX\n")
                  .append("        TEST AX, AX\n")
                  .append("        JNZ LOOP_Enteros\n\n");

                // Imprimir la parte entera
                sb.append("    ;Imprimir parte entera\n")
                  .append("    LEA DX, Enteros\n")
                  .append("    MOV AH, 09h\n")
                  .append("    INT 21h\n\n");

                // Conversión de la parte decimal
                sb.append("    ;Conversión de '").append(variableIzquierda).append("_D' a texto (Decimales)\n")
                  .append("    MOV AX, ").append(variableIzquierda).append("_D\n")
                  .append("    MOV CX, 5\n")
                  .append("    LEA DI, ").append(variableIzquierda).append("_D\n")
                  .append("    MOV BX, 10\n\n")
                  .append("    LOOP_Decimales:\n")
                  .append("        XOR DX, DX\n")
                  .append("        DIV BX\n")
                  .append("        ADD DL, '0'\n")
                  .append("        DEC DI\n")
                  .append("        MOV [DI], DL\n")
                  .append("        DEC CX\n")
                  .append("        TEST AX, AX\n")
                  .append("        JNZ LOOP_Decimales\n\n");

                // Imprimir el punto
                sb.append("    ;Imprimir punto\n")
                  .append("    LEA DX, Punto\n")
                  .append("    MOV AH, 09h\n")
                  .append("    INT 21h\n\n");

                // Imprimir la parte decimal
                sb.append("    ;Imprimir parte decimal\n")
                  .append("    LEA DX, Decimales\n")
                  .append("    MOV AH, 09h\n")
                  .append("    INT 21h\n\n");

                // 6) Finalización del programa ASM
                sb.append("    MOV AH, 4Ch\n")
                  .append("    INT 21h\n")
                  .append("END start\n");

                // Escribir todo el contenido al archivo de una sola vez
                writer.write(sb.toString());
            }
        }
        /**
         * Convierte una cadena de caracteres en su representación decimal separada por comas
         * @param cadena La cadena de entrada a convertir.
         * @return Una cadena con los valores decimales ASCII separados por comas.
         */
        private static String convertirCadenaADecimal(String cadena) {
            StringBuilder decimal = new StringBuilder();
            for (char c : cadena.toCharArray()) {
                decimal.append((int) c).append(",");
            }
            // Eliminar la última coma
            if (decimal.length() > 0) {
                decimal.setLength(decimal.length() - 1);
            }
            return decimal.toString();
        }
        // ---------------------------------------------------------------------------------
        // MÉTODOS AUXILIARES
        // ---------------------------------------------------------------------------------
        /**
         * Convierte un valor numérico a su representación en ASM, excluyendo temporales y la variable principal.
         * @param variable          Nombre de la variable.
         * @param valor             Valor numérico asociado a la variable.
         * @param variableIzquierda La variable principal que debe ser omitida.
         * @return Una cadena formateada con las declaraciones ASM correspondientes, o una cadena vacía si la variable debe ser omitida.
         */
        private static String convertirValorASM(String variable, double valor, String variableIzquierda) {
            // Omitir temporales (prefijos "T") y la variable principal
            if (variable.startsWith("T") || variable.equalsIgnoreCase(variableIzquierda)) {
                return ""; // No generar nada para temporales o la variable principal
            }

            // Convertir el valor a una cadena con formato explícito usando Locale.US
            String resultado = String.format(Locale.US, "%.3f", valor);
            String[] partes = resultado.split("\\."); // Separar parte entera y decimal por el punto

            // Convertir las partes a enteros para su procesamiento
            int parteEntera = Integer.parseInt(partes[0]);
            int parteDecimal = Integer.parseInt(partes[1]); // Tomar todos los tres dígitos decimales

            // Generar declaraciones en formato ASM
            return String.format("%s DW %d\n    %s_D DW %03d\n", variable, parteEntera, variable, parteDecimal);
        }
    }
