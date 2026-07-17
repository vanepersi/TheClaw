package dev.genesi.theclaw.economy;

import dev.genesi.theclaw.TheClawPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class EconomyService {

    private final TheClawPlugin plugin;
    private Economy vault;
    private boolean usingVault;

    public EconomyService(TheClawPlugin plugin) {
        this.plugin = plugin;
    }

    public void hook() {
        vault = null;
        usingVault = false;
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration != null) {
            vault = registration.getProvider();
            usingVault = vault != null;
        }
    }

    public String describe() {
        return usingVault ? "Vault (" + vault.getName() + ")" : "none (entry fees disabled unless 0)";
    }

    public boolean isReady() {
        return usingVault;
    }

    public double getBalance(OfflinePlayer player) {
        return usingVault ? vault.getBalance(player) : 0.0;
    }

    public boolean has(OfflinePlayer player, double amount) {
        return !usingVault || amount <= 0 || vault.has(player, amount);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (amount <= 0) {
            return true;
        }
        if (!usingVault) {
            return false;
        }
        return vault.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (amount <= 0) {
            return true;
        }
        if (!usingVault) {
            return false;
        }
        return vault.depositPlayer(player, amount).transactionSuccess();
    }

    public String format(double amount) {
        if (usingVault) {
            return vault.format(amount);
        }
        if (amount == Math.rint(amount)) {
            return String.valueOf((long) amount);
        }
        return String.format("%.2f", amount);
    }

    public boolean charge(Player player, double amount) {
        if (amount <= 0 || player.hasPermission("theclaw.bypass.fee")) {
            return true;
        }
        if (!usingVault) {
            return false;
        }
        return withdraw(player, amount);
    }
}
