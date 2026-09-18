package genesis.adapter.command;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

public final class SpeedCommand extends CommandBase {

    private static final float CREATIVE_FLIGHT_SPEED = 0.05F;

    @Override
    public String getCommandName() {
        return "speed";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/speed <positive integer>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            throw new WrongUsageException("commands.speed.playerOnly");
        }
        if (args.length != 1) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        final int multiplier;
        try {
            multiplier = Integer.parseInt(args[0]);
        } catch (NumberFormatException exception) {
            throw new WrongUsageException("commands.speed.invalid", args[0]);
        }
        if (multiplier < 1) {
            throw new WrongUsageException("commands.speed.invalid", args[0]);
        }

        EntityPlayerMP player = (EntityPlayerMP) sender;
        player.capabilities.setFlySpeed(CREATIVE_FLIGHT_SPEED * multiplier);
        player.sendPlayerAbilities();
        player.addChatMessage(
            new ChatComponentText(
                "Creative flight speed set to " + multiplier + "x (" + player.capabilities.getFlySpeed() + ")"));
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return null;
    }
}
