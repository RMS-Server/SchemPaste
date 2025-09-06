package net.rms.schempaste.api;

public interface IWorldUpdateSuppressor {
    boolean schempaste_shouldPreventUpdates();
    
    void schempaste_setPreventUpdates(boolean prevent);
}
