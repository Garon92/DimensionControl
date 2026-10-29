package cz.dmuzik.dimensioncontrol.asm;

import java.util.Iterator;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Injects a cancellable check at the head of the two methods every standard dimension change goes through:
 * <ul>
 * <li>{@code EntityPlayerMP.travelToDimension(int)} - vanilla Nether/End portals and mods that call it. Has to be
 * hooked separately because vanilla moves the player to the End spawn platform coordinates <i>before</i> calling
 * the transfer, which would strand a blocked player at those coordinates in the Overworld.</li>
 * <li>{@code ServerConfigurationManager.transferPlayerToDimension(EntityPlayerMP, int, Teleporter)} - the Forge
 * overload that the vanilla 2-arg method delegates to and that dimension mods call with their own teleporters.</li>
 * </ul>
 */
public class DCTransformer implements IClassTransformer {

    private static final Logger LOG = LogManager.getLogger("DimensionControl");

    private static final String HOOKS = "cz/dmuzik/dimensioncontrol/Hooks";
    private static final String HOOK_DESC = "(Lnet/minecraft/entity/player/EntityPlayerMP;I)Z";

    private static final String SCM = "net.minecraft.server.management.ServerConfigurationManager";
    private static final String PLAYER_MP = "net.minecraft.entity.player.EntityPlayerMP";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        if (SCM.equals(transformedName)) {
            // Forge-added overload, it has no SRG name so it keeps its readable name in production too
            return patch(
                basicClass,
                transformedName,
                new String[] { "transferPlayerToDimension" },
                "(Lnet/minecraft/entity/player/EntityPlayerMP;ILnet/minecraft/world/Teleporter;)V",
                1,
                2,
                "onTransferPlayerToDimension");
        }
        if (PLAYER_MP.equals(transformedName)) {
            return patch(
                basicClass,
                transformedName,
                new String[] { "travelToDimension", "func_71027_c" },
                "(I)V",
                0,
                1,
                "onTravelToDimension");
        }
        return basicClass;
    }

    private byte[] patch(byte[] bytes, String className, String[] methodNames, String desc, int playerSlot, int dimSlot,
        String hook) {
        ClassNode classNode = new ClassNode();
        new ClassReader(bytes).accept(classNode, 0);

        boolean patched = false;
        for (MethodNode method : classNode.methods) {
            if (!desc.equals(method.desc) || !nameMatches(method.name, methodNames)) {
                continue;
            }
            injectHeadCheck(method, classNode.version, playerSlot, dimSlot, hook);
            patched = true;
        }

        if (!patched) {
            LOG.error("Could not find {}{} in {} - the hook is NOT active, only the fallback check will work",
                methodNames[0], desc, className);
            return bytes;
        }

        // COMPUTE_FRAMES is avoided on purpose: it would need to load game classes from inside a transformer
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        if (PLAYER_MP.equals(className)) {
            HookStatus.travelToDimensionPatched = true;
        } else {
            HookStatus.transferPlayerToDimensionPatched = true;
        }
        LOG.info("Injected dimension access hook into {}.{}{}", className, methodNames[0], desc);
        return writer.toByteArray();
    }

    private static boolean nameMatches(String name, String[] names) {
        for (String candidate : names) {
            if (candidate.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Prepends: {@code if (!Hooks.hook(player, dim)) return;}
     */
    private static void injectHeadCheck(MethodNode method, int classVersion, int playerSlot, int dimSlot,
        String hook) {
        LabelNode proceed = new LabelNode();
        InsnList check = new InsnList();
        check.add(new VarInsnNode(Opcodes.ALOAD, playerSlot));
        check.add(new VarInsnNode(Opcodes.ILOAD, dimSlot));
        check.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, hook, HOOK_DESC, false));
        check.add(new JumpInsnNode(Opcodes.IFNE, proceed));
        check.add(new InsnNode(Opcodes.RETURN));
        check.add(proceed);

        // Java 7+ class files need a stack map frame at the jump target. At the very start of the method
        // the frame is just the method arguments, i.e. F_SAME relative to the implicit initial frame.
        boolean needsFrame = (classVersion & 0xFFFF) >= Opcodes.V1_7 || hasFrames(method);
        if (needsFrame && !startsWithFrame(method)) {
            check.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        }
        method.instructions.insert(check);
    }

    private static boolean hasFrames(MethodNode method) {
        for (Iterator<AbstractInsnNode> it = method.instructions.iterator(); it.hasNext();) {
            if (it.next() instanceof FrameNode) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWithFrame(MethodNode method) {
        for (Iterator<AbstractInsnNode> it = method.instructions.iterator(); it.hasNext();) {
            AbstractInsnNode node = it.next();
            if (node instanceof FrameNode) {
                return true;
            }
            if (node.getOpcode() >= 0) {
                return false;
            }
        }
        return false;
    }
}
