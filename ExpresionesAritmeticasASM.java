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
 * valida su estructura, y genera un archivo Ensamblador (ASM) con ofuscación
 * en la parte de impresión y manejo de resultados negativos.
 */
public class ExpresionesAritmeticasASM {

    // ---------------------------------------------------------------------------------
    //                          EXCEPCIONES PERSONALIZADAS
    // ---------------------------------------------------------------------------------
    static class InvalidExpressionException extends RuntimeException {
        public InvalidExpressionException(String message) {
            super(message);
        }
    }

    // ---------------------------------------------------------------------------------
    //                                PROPIEDADES
    // ---------------------------------------------------------------------------------
    private static int temporalCounter = 1;  // Contador para variables temporales
    private static final int NOMBRE_RANDOM_LEN = 5; // Tamaño por defecto para nombres random

    // ---------------------------------------------------------------------------------
    //                             MÉTODO PRINCIPAL
    // ---------------------------------------------------------------------------------
    @SuppressWarnings("ConvertToTryWithResources")
    public static void main(String[] args) {
        // Selector de archivos
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Seleccione un archivo .txt");

        // Leer el archivo .txt
        int result = fileChooser.showOpenDialog(null);
        if (result != JFileChooser.APPROVE_OPTION) {
            System.out.println("No se seleccionó ningún archivo.");
            return;
        }

        File selectedFile = fileChooser.getSelectedFile();
        if (!selectedFile.getName().endsWith(".txt")) {
            System.err.println("Por favor, seleccione un archivo con extensión .txt");
            return;
        }

        // Cargar la expresión del archivo (sin espacios)
        String expresionAritmetica;
        try {
            String contenido = new String(Files.readAllBytes(Paths.get(selectedFile.getAbsolutePath())));
            expresionAritmetica = contenido.replaceAll("\\s+", "");
        } catch (IOException e) {
            System.err.println("Error al leer el archivo: " + e.getMessage());
            return;
        }

        // Mostrar la expresión en consola
        System.out.println("\nExpresión Aritmética: " + expresionAritmetica + "\n");

        // Validar la expresión
        if (!esExpresionValida(expresionAritmetica)) {
            throw new InvalidExpressionException("La expresión es inválida (operadores consecutivos, paréntesis mal, etc.).");
        }

        // Identificar variable izquierda y el resto de variables
        String variableIzquierda = identificarVariableIzquierda(expresionAritmetica);
        Set<String> variables = identificarVariables(expresionAritmetica);
        variables.remove(variableIzquierda);

        // Pedir valores de las variables
        Scanner scanner = new Scanner(System.in);
        Map<String, Double> valoresVariables = obtenerValoresDeVariables(variables, scanner);
        scanner.close();

        // Reemplazar las variables en la expresión con su propio nombre
        String input = expresionAritmetica;
        for (Map.Entry<String, Double> entry : valoresVariables.entrySet()) {
            input = input.replaceAll("\\b" + Pattern.quote(entry.getKey()) + "\\b", entry.getKey());
        }

        // Generar estructuras para temporales e instrucciones ASM
        List<String> temporales = new ArrayList<>();
        List<String> instruccionesASM = new ArrayList<>();

        // Procesar la expresión y generar ASM
        procesarExpresion(input, temporales, instruccionesASM, valoresVariables);

        // Mostrar en consola las operaciones intermedias
        System.out.println();
        for (String temp : temporales) {
            System.out.println(temp);
        }

        // Resultado calculado en Java
        double resultadoNumerico = valoresVariables.get(variableIzquierda);
        System.out.println("\n - Resultado numérico calculado: " + String.format("%.3f", resultadoNumerico) + "\n");

        // Definir la cadena que representará el resultado en ASM (si negativo, anteponemos '-')
        String resultadoFinalJava = (resultadoNumerico < 0)
                ? "-" + String.format("%.3f", Math.abs(resultadoNumerico))
                : String.format("%.3f", resultadoNumerico);

        // Verificar si es negativo
        boolean esNegativo = (resultadoNumerico < 0);

        // Generar el archivo ASM
        try {
            generarArchivoASM(instruccionesASM, valoresVariables, variableIzquierda, resultadoFinalJava, esNegativo);
            System.out.println(" - Archivo ASM generado exitosamente: Resultado.ASM\n");
        } catch (IOException e) {
            System.err.println(" - Error al generar el archivo ASM: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------------------------
    //                         VALIDACIÓN DE LA EXPRESIÓN
    // ---------------------------------------------------------------------------------
    /**
     * Valida la estructura global de una expresión, revisando:
     *   - Solo un '='
     *   - No operadores consecutivos
     *   - Paréntesis balanceados
     *   - Identificador válido a la izquierda
     *   - Sin caracteres no permitidos
     */
    private static boolean esExpresionValida(String expresion) {
        // Verificar que no haya operadores consecutivos
        Pattern operadoresConsecutivos = Pattern.compile("[+\\-*/=]{2,}");
        Matcher matcherOperadores = operadoresConsecutivos.matcher(expresion);
        if (matcherOperadores.find()) {
            return false; // Falla si encuentra operadores consecutivos
        }

        // Validar que no haya paréntesis sin operador antes (p.ej. a(b))
        Pattern parenSinOperador = Pattern.compile("(?<![+\\-*/(=])\\(");
        Matcher matcherParenSinOperador = parenSinOperador.matcher(expresion);
        if (matcherParenSinOperador.find()) {
            return false; // Falla si el paréntesis no tiene un operador antes
        }

        // Verificar que no haya números seguidos de '(' sin operador (p.ej. 5(3+2))
        Pattern numeroSinOperador = Pattern.compile("\\d+\\(");
        Matcher matcherNumeroSinOperador = numeroSinOperador.matcher(expresion);
        if (matcherNumeroSinOperador.find()) {
            return false; // Falla si un número va inmediatamente seguido de '('
        }

        // Verificar que haya exactamente un '='
        long countIgual = expresion.chars().filter(ch -> ch == '=').count();
        if (countIgual != 1) {
            return false; // Falla si no hay '=' o hay más de uno
        }

        // Validar que el lado izquierdo del '=' sea un identificador válido
        String[] partes = expresion.split("=", 2);
        String ladoIzquierdo = partes[0];
        if (!ladoIzquierdo.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            return false; // Falla si no es un nombre de variable válido
        }

        // Verificar que no haya caracteres inválidos en la expresión
        Pattern caracteresInvalidos = Pattern.compile("[^a-zA-Z0-9_+\\-*/=().]");
        Matcher matcherCaracteresInvalidos = caracteresInvalidos.matcher(expresion);
        if (matcherCaracteresInvalidos.find()) {
            return false; // Falla si encuentra símbolos no permitidos
        }

        // Verificar balance de paréntesis
        int contadorParentesis = 0;
        for (char c : expresion.toCharArray()) {
            if (c == '(')
                contadorParentesis++;
            if (c == ')')
                contadorParentesis--;
            if (contadorParentesis < 0) {
                return false; // Falla si hay más ')' que '('
            }
        }
        if (contadorParentesis != 0) {
            return false; // Falla si no hay balance total
        }

        // Si llega aquí, la expresión es válida
        return true;
    }

    /**
     * Separa la parte izquierda del '=' para obtener la variable asignada.
     */
    private static String identificarVariableIzquierda(String expresion) {
        int idx = expresion.indexOf('=');
        return (idx != -1) ? expresion.substring(0, idx) : null;
    }

    /**
     * Identifica todas las variables dentro de la expresión (a-z, 0-9 y '_').
     */
    private static Set<String> identificarVariables(String expresion) {
        Set<String> vars = new HashSet<>();
        Matcher m = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*").matcher(expresion);
        while (m.find()) {
            vars.add(m.group());
        }
        return vars;
    }

    // ---------------------------------------------------------------------------------
    //                  PEDIR VALORES PARA LAS VARIABLES DESDE CONSOLA
    // ---------------------------------------------------------------------------------
    /**
     * Pide al usuario los valores numéricos para cada variable.
     */
    private static Map<String, Double> obtenerValoresDeVariables(Set<String> variables, Scanner scanner) {
        Map<String, Double> map = new HashMap<>();
        for (String var : variables) {
            while (true) {
                System.out.print(" * Ingrese el valor para la variable '" + var + "': ");
                String entrada = scanner.next();
                try {
                    double val = Double.parseDouble(entrada);
                    map.put(var, val);
                    break;
                } catch (NumberFormatException ex) {
                    System.out.println("Error: Solo se permiten números.");
                }
            }
        }
        return map;
    }

    // ---------------------------------------------------------------------------------
    //                   PROCESAR LA EXPRESIÓN Y GENERAR INSTRUCCIONES ASM
    // ---------------------------------------------------------------------------------
    private static String procesarExpresion(
            String expresion,
            List<String> temporales,
            List<String> instruccionesASM,
            Map<String, Double> valoresVariables
    ) {
        String[] operadoresJerarquia = { "\\(", "\\*", "/", "\\+", "-", "=" };
        String[] nombresOperadores   = { "PAREN", "MUL", "DIV", "ADD", "SUB", "MOV" };

        // Primero procesamos paréntesis de manera recursiva
        Pattern patParentesis = Pattern.compile("\\(([^()]+)\\)");
        Matcher matParen;
        while ((matParen = patParentesis.matcher(expresion)).find()) {
            String subExp = matParen.group(1);
            String temp = procesarExpresion(subExp, temporales, instruccionesASM, valoresVariables);
            expresion = expresion.replaceFirst(Pattern.quote("(" + subExp + ")"), temp);
        }

        // Luego iteramos operadores en orden
        for (int i = 1; i < operadoresJerarquia.length; i++) {
            String regex = String.format(
                    "([a-zA-Z_][a-zA-Z0-9_]*|\\d+(\\.\\d+)?|T\\d+)%s([a-zA-Z_][a-zA-Z0-9_]*|\\d+(\\.\\d+)?|T\\d+)",
                    operadoresJerarquia[i]
            );

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

                String operacion = String.format("    %s -> %s, %s, %s", tempVar, op1, op2, nombresOperadores[i]);
                temporales.add(operacion);

                String asm = generarInstruccionASM(nombresOperadores[i], op1, op2, tempVar);
                instruccionesASM.add(asm);

                expresion = expresion.replaceFirst(Pattern.quote(m.group(0)), tempVar);
            }
        }

        return expresion;
    }

    /**
     * Realiza la operación aritmética entre dos operandos, en Java,
     * para luego almacenar el resultado en un "temporal".
     */
    private static double calcularResultado(double a, double b, String op) {
        return switch (op) {
            case "MUL" -> a * b;
            case "DIV" -> a / b;
            case "ADD" -> a + b;
            case "SUB" -> a - b;
            default    -> throw new IllegalArgumentException("Operador no soportado: " + op);
        };
    }

    /**
     * Genera la instrucción ASM para un operador específico y dos operandos.
     * Se agrega alguna instrucción "neutra" (like XOR AX,AX) para ofuscar.
     */
    private static String generarInstruccionASM(String operador, String op1, String op2, String tempVar) {
        StringBuilder sb = new StringBuilder();

        // Ofuscación: insertar instrucción neutra
        sb.append("\n    XOR AX, AX ; Ofuscación (instrucción neutra)\n");
        sb.append("    MOV AX, AX ; Aún más redundancia\n");

        switch (operador) {
            case "MUL" -> {
                sb.append(String.format("    MOV AX, %s", op1)).append("\n");
                sb.append(String.format("    MOV BX, %s", op2)).append("\n");
                sb.append("    CWD\n");
                sb.append("    IMUL BX\n");
                sb.append(String.format("    MOV %s, AX", tempVar));
            }
            case "DIV" -> {
                sb.append(String.format("    MOV AX, %s", op1)).append("\n");
                sb.append("    XOR DX, DX\n");
                sb.append(String.format("    MOV BX, %s", op2)).append("\n");
                sb.append("    CWD\n");
                sb.append("    IDIV BX\n");
                sb.append(String.format("    MOV %s, AX", tempVar));
            }
            case "ADD" -> {
                sb.append(String.format("    MOV AX, %s", op1)).append("\n");
                sb.append(String.format("    ADD AX, %s", op2)).append("\n");
                sb.append(String.format("    MOV %s, AX", tempVar));
            }
            case "SUB" -> {
                sb.append(String.format("    MOV AX, %s", op1)).append("\n");
                sb.append(String.format("    SUB AX, %s", op2)).append("\n");
                sb.append(String.format("    MOV %s, AX", tempVar));
            }
            case "MOV" -> {
                sb.append(String.format("    MOV AX, %s", op2)).append("\n");
                sb.append(String.format("    MOV %s, AX", op1));
            }
            default -> throw new IllegalArgumentException("Operador no soportado: " + operador);
        }

        return sb.toString();
    }

    // ---------------------------------------------------------------------------------
    //                    GENERACIÓN DEL ARCHIVO ASM (OFUSCADO)
    // ---------------------------------------------------------------------------------

    /**
     * Genera el archivo "Resultado.ASM" con:
     *  - Declaración de variables (parte entera, parte decimal).
     *  - Etiquetas y buffers con nombres aleatorios.
     *  - Lógica ofuscada para imprimir resultados.
     */
    private static void generarArchivoASM(
            List<String> instruccionesASM,
            Map<String, Double> valoresVariables,
            String variableIzquierda,
            String resultadoFinalJava,
            boolean esNegativo
    ) throws IOException {
        try (FileWriter writer = new FileWriter("Resultado.ASM")) {
            // Etiquetas y nombres de buffers random
            String labelCasoNoNeg = generarNombreAleatorio("L_", NOMBRE_RANDOM_LEN);
            String labelCasoNeg   = generarNombreAleatorio("B_", NOMBRE_RANDOM_LEN);
            String bufferTitulo   = generarNombreAleatorio("BUF_", NOMBRE_RANDOM_LEN);
            String bufferEntero   = generarNombreAleatorio("BUF_", NOMBRE_RANDOM_LEN);
            String bufferPunto    = generarNombreAleatorio("BUF_", NOMBRE_RANDOM_LEN);
            String bufferDecimal  = generarNombreAleatorio("BUF_", NOMBRE_RANDOM_LEN);
            String finPrograma    = generarNombreAleatorio("END_", NOMBRE_RANDOM_LEN);

            // dummy variable para resultado negativo
            String varNegativo = generarNombreAleatorio("DUM_", NOMBRE_RANDOM_LEN);

            // 1) Encabezado
            agregarEncabezado(writer);

            // 2) Declarar variables en .DATA
            for (Map.Entry<String, Double> entry : valoresVariables.entrySet()) {
                writer.write("    " + entry.getKey() + " DW "
                             + convertirValorASM(entry.getKey(), entry.getValue()) + "\n");
            }

            // 3) Declaraciones base
            writer.write("    " + bufferTitulo + " DB '" + variableIzquierda + " =   $'\n");
            writer.write("    " + bufferEntero + " DB 5 DUP('$')\n");
            writer.write("    " + bufferPunto + "  DB '.  $'\n");
            writer.write("    " + bufferDecimal + " DB 5 DUP('$')\n\n");

            // Solo si es negativo, declaramos la variable disfrazada
            if (esNegativo) {
                writer.write("    " + varNegativo + " DB \"" + resultadoFinalJava + "\", '$'\n\n");
            }

            // 4) Segmento de código
            agregarSegmentoCodigoInicio(writer);

            // 5) Instrucciones generadas
            for (String instruccion : instruccionesASM) {
                writer.write("    " + instruccion + "\n");
            }

            // 6) Según esNegativo, generamos un label para la parte de impresión
            if (!esNegativo) {
                // NO NEGATIVO
                writer.write(labelCasoNoNeg + ":\n");
                // Convertir parte entera
                // (Ofuscación: instrucción redundante)
                writer.write("    MOV AX, AX ; NEUTRAL OP\n");

                writer.write("    MOV AX, " + variableIzquierda + "\n");
                writer.write("    MOV CX, 5\n");
                writer.write("    LEA DI, " + bufferEntero + "\n");
                writer.write("    MOV BX, 10\n");
                writer.write("\nLOOP_1:\n");
                writer.write("    XOR DX, DX\n");
                writer.write("    DIV BX\n");
                writer.write("    ADD DL, '0'\n");
                writer.write("    DEC DI\n");
                writer.write("    MOV [DI], DL\n");
                writer.write("    DEC CX\n");
                writer.write("    TEST AX, AX\n");
                writer.write("    JNZ LOOP_1\n\n");

                // Imprime el "var ="
                writer.write("    LEA DX, " + bufferTitulo + "\n");
                writer.write("    MOV AH, 09h\n");
                writer.write("    INT 21h\n");

                // Imprime parte entera
                writer.write("    LEA DX, " + bufferEntero + "\n");
                writer.write("    MOV AH, 09h\n");
                writer.write("    INT 21h\n\n");

                // Parte decimal
                writer.write("    MOV AX, " + variableIzquierda + "_D\n");
                writer.write("    MOV CX, 5\n");
                writer.write("    LEA DI, " + bufferDecimal + "\n");
                writer.write("    MOV BX, 10\n");
                writer.write("\nLOOP_2:\n");
                writer.write("    XOR DX, DX\n");
                writer.write("    DIV BX\n");
                writer.write("    ADD DL, '0'\n");
                writer.write("    DEC DI\n");
                writer.write("    MOV [DI], DL\n");
                writer.write("    DEC CX\n");
                writer.write("    TEST AX, AX\n");
                writer.write("    JNZ LOOP_2\n\n");

                // Imprime el punto
                writer.write("    LEA DX, " + bufferPunto + "\n");
                writer.write("    MOV AH, 09h\n");
                writer.write("    INT 21h\n");

                // Imprime decimales
                writer.write("    LEA DX, " + bufferDecimal + "\n");
                writer.write("    MOV AH, 09h\n");
                writer.write("    INT 21h\n\n");

            } else {
                // CASO NEGATIVO
                writer.write(labelCasoNeg + ":\n");
                // Imprimimos la variable ofuscada
                writer.write("    LEA DX, " + varNegativo + "\n");
                writer.write("    MOV AH, 09h\n");
                writer.write("    INT 21h\n\n");
            }

            // 7) Fin de programa
            writer.write(finPrograma + ":\n");
            writer.write("    MOV AH, 4Ch\n");
            writer.write("    INT 21h\n");
            writer.write("END start\n");
        }
    }

    // ---------------------------------------------------------------------------------
    //                              MÉTODOS AUXILIARES
    // ---------------------------------------------------------------------------------

    /**
     * Genera un nombre aleatorio usando letras mayúsculas y dígitos, con un prefijo.
     */
    private static String generarNombreAleatorio(String prefijo, int longitud) {
        String caracteres = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder nombre = new StringBuilder(prefijo);
        Random random = new Random();
        for (int i = 0; i < longitud; i++) {
            nombre.append(caracteres.charAt(random.nextInt(caracteres.length())));
        }
        return nombre.toString();
    }

    /**
     * Agrega el encabezado .MODEL, .STACK, etc. al FileWriter.
     */
    private static void agregarEncabezado(FileWriter writer) throws IOException {
        writer.write(".MODEL SMALL\n");
        writer.write(".STACK 100h\n\n");
        writer.write(".DATA\n");
    }

    /**
     * Agrega la sección .CODE, la directiva start y la inicialización de DS.
     */
    private static void agregarSegmentoCodigoInicio(FileWriter writer) throws IOException {
        writer.write(".CODE\n");
        writer.write("start:\n");
        writer.write("    MOV AX, @DATA\n");
        writer.write("    MOV DS, AX\n\n");
    }

    /**
     * Convierten valor double a la parte entera y decimal (DW).
     * Incluye el "carry" si la parte decimal redondeada llega a 1000.
     */
    private static String convertirValorASM(String variable, double valor) {
        int parteEntera = (int) valor;
        double decimal = valor - parteEntera;
        decimal = Math.abs(decimal);

        int parteDecimal = (int) Math.round(decimal * 1000);
        if (parteDecimal == 1000) {
            parteEntera += (valor >= 0) ? 1 : -1;
            parteDecimal = 0;
        }

        // Formatear
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%03d", parteEntera));  // primera DW (parte entera)
        sb.append("\n    ");
        sb.append(variable).append("_D DW ");
        sb.append(String.format("%03d", parteDecimal)); // segunda DW (parte decimal)
        sb.append(" ; decimales\n");
        return sb.toString();
    }
}
