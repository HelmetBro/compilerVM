// The DLX Virtual Machine
// chs / mf 2001-08-07

import java.io.*;
import java.util.Scanner;

// All variables and methods are realized as class variables/methods which
// means that just one processor can be emulated at a time.

public class DLX {
    // processor state variables
    static int R[] = new int [32];
    static int PC, op, a, b, c, format;

    // emulated memory
    static final int MemSize = 10000; // bytes in memory (divisible by 4)
    static int M[] = new int [MemSize/4];


    public static void main(String argv[]) throws IOException {

        File file = new File("/home/eric/CLionProjects/241Compiler/cmake-build-debug/output0.241");
        Scanner scan = new Scanner(file);

        int[][] input = new int[MemSize][4];

        int size = 0;
        while(scan.hasNext()){
            //single instruction
            input[size][0] = scan.nextInt();
            input[size][1] = scan.nextInt();
            input[size][2] = scan.nextInt();
            input[size++][3] = scan.nextInt();
        }

        scan.close();

        int[] program = new int[size];

        //I'm going to get so much dislike for doing this...
        int op_code = -1;
        for(int i = 0; i < size; ++i){
            try{ op_code = assemble(input[i][0], input[i][1], input[i][2], input[i][3]); } catch (Exception e1){
                try{ op_code = assemble(input[i][0], input[i][1], input[i][2]); }catch (Exception e2){
                    try{ op_code = assemble(input[i][0], input[i][1]); }catch (Exception e3) {
                        try { op_code = assemble(input[i][0]); } catch (Exception e4) {
                            System.exit(-1);
                        }
                    }
                }
            }

            program[i] = op_code;
        }

        load(program);

        for (int i1 : program) System.out.print(disassemble(i1));

        execute();

    }

    public static void load(int program[]) {
        int i;
        for (i = 0; i < program.length; i++) {
            M[i] = program[i];
        }
        M[i] = -1; // set first opcode of first instruction after program
        // to ERR in order to detect 'fall off the edge' errors
    }

    public static void execute() throws IOException {
        int origc = 0; // used for F2 instruction RET
        for (int i = 0; i < 32; i++) { R[i] = 0; };
        PC = 0; R[30] = MemSize - 1;

        try {

            execloop:
            while (true) {
                R[0] = 0;
                disassem(M[PC]); // initializes op, a, b, c

                int nextPC = PC + 1;
                if (format==2) {
                    origc = c; // used for RET
                    c = R[c];  // dirty trick
                }
                switch (op) {
                    case ADD:
                    case ADDI:
                        R[a] = R[b] + c;
                        break;
                    case SUB:
                    case SUBI:
                        R[a] = R[b] - c;
                        break;
                    case CMP:
                    case CMPI:
                        R[a] = R[b] - c; // can not create overflow
                        if (R[a] < 0) R[a] = -1;
                        else if (R[a] > 0) R[a] = 1;
                        // we don't have to do anything if R[a]==0
                        break;
                    case MUL:
                    case MULI:
                        R[a] = R[b] * c;
                        break;
                    case DIV:
                    case DIVI:
                        R[a] = R[b] / c;
                        break;
                    case MOD:
                    case MODI:
                        R[a] = R[b] % c;
                        break;
                    case OR:
                    case ORI:
                        R[a] = R[b] | c;
                        break;
                    case AND:
                    case ANDI:
                        R[a] = R[b] & c;
                        break;
                    case BIC:
                    case BICI:
                        R[a] = R[b] & ~c;
                        break;
                    case XOR:
                    case XORI:
                        R[a] = R[b] ^ c;
                        break;
                    // Shifts: - a shift by a positive number means a left shift
                    //         - if c > 31 or c < -31 an error is generated
                    case LSH:
                    case LSHI:
                        if ((c < -31) || (c >31)) {
                            System.out.println("Illegal value " + c +
                                    " of operand c or register c!");
                            bug(1);
                        }
                        if (c < 0)  R[a] = R[b] >>> -c;
                        else        R[a] = R[b] << c;
                        break;
                    case ASH:
                    case ASHI:
                        if ((c < -31) || (c >31)) {
                            System.out.println("DLX.execute: Illegal value " + c +
                                    " of operand c or register c!");
                            bug(1);
                        }
                        if (c < 0)  R[a] = R[b] >> -c;
                        else        R[a] = R[b] << c;
                        break;
                    case CHKI:
                    case CHK:
                        if (R[a] < 0) {
                            System.out.println("DLX.execute: " + PC*4 + ": R[" + a + "] == " +
                                    R[a] + " < 0");
                            bug(14);
                        } else if (R[a] >= c) {
                            System.out.println("DLX.execute: " + PC*4 + ": R[" + a + "] == " +
                                    R[a] + " >= " + c);
                            bug(14);
                        }
                        break;
                    case LDW:
                    case LDX: // remember: c == R[origc] because of F2 format
                        R[a] = M[(R[b]+c) / 4];
                        break;
                    case STW:
                    case STX: // remember: c == R[origc] because of F2 format
                        M[(R[b]+c) / 4] = R[a];
                        break;
                    case POP:
                        R[a] = M[R[b] / 4];
                        R[b] = R[b] + c;
                        break;
                    case PSH:
                        R[b] = R[b] + c;
                        M[R[b] / 4] = R[a];
                        break;
                    case BEQ:
                        if (R[a] == 0) nextPC = PC + c;
                        if ((nextPC < 0) || (nextPC > MemSize/4)) {
                            System.out.println(4*nextPC + " is no address in memory (0.."
                                    + MemSize + ").");
                            bug(40);
                        }
                        break;
                    case BNE:
                        if (R[a] != 0) nextPC = PC + c;
                        if ((nextPC < 0) || (nextPC > MemSize/4)) {
                            System.out.println(4*nextPC + " is no address in memory (0.."
                                    + MemSize + ").");
                            bug(41);
                        }
                        break;
                    case BLT:
                        if (R[a] < 0) nextPC = PC + c;
                        if ((nextPC < 0) || (nextPC > MemSize/4)) {
                            System.out.println(4*nextPC + " is no address in memory (0.."
                                    + MemSize + ").");
                            bug(42);
                        }
                        break;
                    case BGE:
                        if (R[a] >= 0) nextPC = PC + c;
                        if ((nextPC < 0) || (nextPC > MemSize/4)) {
                            System.out.println(4*nextPC + " is no address in memory (0.."
                                    + MemSize + ").");
                            bug(43);
                        }
                        break;
                    case BLE:
                        if (R[a] <= 0) nextPC = PC + c;
                        if ((nextPC < 0) || (nextPC > MemSize/4)) {
                            System.out.println(4*nextPC + " is no address in memory (0.."
                                    + MemSize + ").");
                            bug(44);
                        }
                        break;
                    case BGT:
                        if (R[a] > 0) nextPC = PC + c;
                        if ((nextPC < 0) || (nextPC > MemSize/4)) {
                            System.out.println(4*nextPC + " is no address in memory (0.."
                                    + MemSize + ").");
                            bug(45);
                        }
                        break;
                    case BSR:
                        R[31] = (PC+1) * 4;
                        nextPC = PC + c;
                        break;
                    case JSR:
                        R[31] = (PC+1) * 4;
                        nextPC = c / 4;
                        break;
                    case RET:
                        if (origc == 0) break execloop; // remember: c==R[origc]
                        if ((c < 0) || (c > MemSize)) {
                            System.out.println(c + " is no address in memory (0.."
                                    + MemSize + ").");
                            bug(49);
                        }
                        nextPC = c / 4;
                        break;
                    case RDI:
                        System.out.print("?: ");
                        String line = (new BufferedReader(new InputStreamReader(System.in))).readLine();
                        R[a] = Integer.parseInt(line);
                        break;
                    case WRD:
                        System.out.print(R[b] + "  ");
                        break;
                    case WRH:
                        System.out.print("0x" + Integer.toHexString(R[b]) + "  ");
                        break;
                    case WRL:
                        System.out.println();
                        break;
                    case ERR:
                        System.out.println("Program dropped off the end!");
                        bug(1);
                        break;
                    default:
                        System.out.println("DLX.execute: Unknown opcode encountered!");
                        bug(1);
                }
                PC = nextPC;
            }

        }
        catch (java.lang.ArrayIndexOutOfBoundsException e ) {
            System.out.println( "failed at " + PC*4 + ",   "  + disassemble( M[PC] ) );
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    // Mnemonic-to-Opcode mapping
    static final String mnemo[] = {
            "ADD","SUB","MUL","DIV","MOD","CMP","ERR","ERR","OR","AND","BIC","XOR","LSH","ASH","CHK","ERR",
            "ADDI","SUBI","MULI","DIVI","MODI","CMPI","ERRI","ERRI","ORI","ANDI","BICI","XORI","LSHI","ASHI","CHKI","ERR",
            "LDW","LDX","POP","ERR","STW","STX","PSH","ERR","BEQ","BNE","BLT","BGE","BLE","BGT","BSR","ERR",
            "JSR","RET","RDI","WRD","WRH","WRL","ERR","ERR","ERR","ERR","ERR","ERR","ERR","ERR","ERR","ERR",
            "ERR","ERR","ERR","ERR","ERR","ERR","ERR","ERR","ERR","ERR","ERR","ERR","ERR","ERR","ERR","ERR"};
    static final int ADD = 0;
    static final int SUB = 1;
    static final int MUL = 2;
    static final int DIV = 3;
    static final int MOD = 4;
    static final int CMP = 5;
    static final int OR  = 8;
    static final int AND = 9;
    static final int BIC = 10;
    static final int XOR = 11;
    static final int LSH = 12;
    static final int ASH = 13;
    static final int CHK = 14;

    static final int ADDI = 16;
    static final int SUBI = 17;
    static final int MULI = 18;
    static final int DIVI = 19;
    static final int MODI = 20;
    static final int CMPI = 21;
    static final int ORI  = 24;
    static final int ANDI = 25;
    static final int BICI = 26;
    static final int XORI = 27;
    static final int LSHI = 28;
    static final int ASHI = 29;
    static final int CHKI = 30;

    static final int LDW = 32;
    static final int LDX = 33;
    static final int POP = 34;
    static final int STW = 36;
    static final int STX = 37;
    static final int PSH = 38;

    static final int BEQ = 40;
    static final int BNE = 41;
    static final int BLT = 42;
    static final int BGE = 43;
    static final int BLE = 44;
    static final int BGT = 45;
    static final int BSR = 46;
    static final int JSR = 48;
    static final int RET = 49;

    static final int RDI = 50;
    static final int WRD = 51;
    static final int WRH = 52;
    static final int WRL = 53;

    static final int ERR = 63; // error opcode which is insertered by loader
    // after end of program code

    static void disassem(int instructionWord) {
        op = instructionWord >>> 26; // without sign extension
        switch (op) {

            // F1 Format
            case BSR:
            case RDI:
            case WRD:
            case WRH:
            case WRL:
            case CHKI:
            case BEQ:
            case BNE:
            case BLT:
            case BGE:
            case BLE:
            case BGT:
            case ADDI:
            case SUBI:
            case MULI:
            case DIVI:
            case MODI:
            case CMPI:
            case ORI:
            case ANDI:
            case BICI:
            case XORI:
            case LSHI:
            case ASHI:
            case LDW:
            case POP:
            case STW:
            case PSH:
                format = 1;
                a = (instructionWord >>> 21) & 0x1F;
                b = (instructionWord >>> 16) & 0x1F;
                c = (short) instructionWord; // another dirty trick
                break;

            // F2 Format
            case RET:
            case CHK:
            case ADD:
            case SUB:
            case MUL:
            case DIV:
            case MOD:
            case CMP:
            case OR:
            case AND:
            case BIC:
            case XOR:
            case LSH:
            case ASH:
            case LDX:
            case STX:
                format = 2;
                a = (instructionWord >>> 21) & 0x1F;
                b = (instructionWord >>> 16) & 0x1F;
                c = instructionWord & 0x1F;
                break;

            // F3 Format
            case JSR:
                format = 3;
                a = -1; // invalid, for error detection
                b = -1;
                c = instructionWord & 0x3FFFFFF;
                break;

            // unknown instruction code
            default:
                System.out.println( "Illegal instruction! (" + PC + ")" );
        }
    }

    static String disassemble(int instructionWord) {

        disassem(instructionWord);
        String line = mnemo[op] + "  ";

        switch (op) {

            case WRL:
                return line += "\n";
            case BSR:
            case RET:
            case JSR:
                return line += c + "\n";
            case RDI:
                return line += a + "\n";
            case WRD:
            case WRH:
                return line += b + "\n";
            case CHKI:
            case BEQ:
            case BNE:
            case BLT:
            case BGE:
            case BLE:
            case BGT:
            case CHK:
                return line += a + " " + c + "\n";
            case ADDI:
            case SUBI:
            case MULI:
            case DIVI:
            case MODI:
            case CMPI:
            case ORI:
            case ANDI:
            case BICI:
            case XORI:
            case LSHI:
            case ASHI:
            case LDW:
            case POP:
            case STW:
            case PSH:
            case ADD:
            case SUB:
            case MUL:
            case DIV:
            case MOD:
            case CMP:
            case OR:
            case AND:
            case BIC:
            case XOR:
            case LSH:
            case ASH:
            case LDX:
            case STX:
                return line += a + " " + b + " " + c + "\n";
            default:
                return line += "\n";
        }
    }

    static int assemble(int op) throws Exception {
        if (op != WRL) {
            System.out.println("DLX.assemble: the only instruction without arguments is WRL!");
            bug(1);
        }
        return F1(op,0,0,0);
    }

    static int assemble(int op, int arg1) throws Exception {
        switch (op) {

            // F1 Format
            case BSR:
                return F1(op,0,0,arg1);
            case RDI:
                return F1(op,arg1,0,0);
            case WRD:
            case WRH:
                return F1(op,0,arg1,0);

            // F2 Format
            case RET:
                return F2(op,0,0,arg1);

            // F3 Format
            case JSR:
                return F3(op,arg1);
            default:
//                System.out.println("DLX.assemble: wrong opcode for one arg instruction!");
                bug(1);
                return -1; // java forces this senseless return statement!
            // I'm thankful for every sensible explanation.
        }
    }

    static int assemble(int op, int arg1, int arg2) throws Exception {
        switch (op) {

            // F1 Format
            case CHKI:
            case BEQ:
            case BNE:
            case BLT:
            case BGE:
            case BLE:
            case BGT:
                return F1(op,arg1,0,arg2);

            // F2 Format
            case CHK:
                return F2(op,arg1,0,arg2);

            default:
//                System.out.println("DLX.assemble: wrong opcode for two arg instruction!");
                bug(1);
                return -1;
        }
    }

    static int assemble(int op, int arg1, int arg2, int arg3) throws Exception {
        switch (op) {

            // F1 Format
            case ADDI:
            case SUBI:
            case MULI:
            case DIVI:
            case MODI:
            case CMPI:
            case ORI:
            case ANDI:
            case BICI:
            case XORI:
            case LSHI:
            case ASHI:
            case LDW:
            case POP:
            case STW:
            case PSH:
                return F1(op,arg1,arg2,arg3);

            // F2 Format
            case ADD:
            case SUB:
            case MUL:
            case DIV:
            case MOD:
            case CMP:
            case OR:
            case AND:
            case BIC:
            case XOR:
            case LSH:
            case ASH:
            case LDX:
            case STX:
                return F2(op,arg1,arg2,arg3);

            default:
//                System.out.println("DLX.assemble: wrong opcode for three arg instruction!");
                bug(1);
                return -1;
        }
    }

    static int F1(int op, int a, int b, int c) throws Exception {
        if (c < 0) c ^= 0xFFFF0000;
        if ((a & ~0x1F | b & ~0x1F | c & ~0xFFFF) != 0) {
            System.out.println("Illegal Operand(s) for F1 Format.");
            bug(1);
        }
        return op << 26 | a << 21 | b << 16 | c;
    }

    static int F2(int op, int a, int b, int c) throws Exception {
        if ((a & ~0x1F | b & ~0x1F | c & ~0x1F) != 0) {
            System.out.println("Illegal Operand(s) for F2 Format.");
            bug(1);
        }
        return op << 26 | a << 21 | b << 16 | c;
    }

    static int F3(int op, int c) throws Exception {
        if ((c < 0) || (c > MemSize)) {
            System.out.println("Operand for F3 Format is referencing " +
                    "non-existent memory location.");
            bug(1);
        }
        return op << 26 | c;
    }

    static void bug(int n) throws Exception {
        throw new Exception();
//        System.out.println("bug number: " + n);
//        try{ System.in.read(); } catch (Exception ee) {;}
//        System.exit(n);
    }


}