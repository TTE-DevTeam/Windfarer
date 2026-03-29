package net.countercraft.movecraft.async;

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.craft.Craft;

public abstract class AsyncTask extends AbstractAsyncTask{

    protected AsyncTask(Craft craft) {
        super(craft);
    }

    @Override
    protected void submitCompletedTask() {
        Movecraft.getInstance().getAsyncManager().submitCompletedTask(this);
    }
}
