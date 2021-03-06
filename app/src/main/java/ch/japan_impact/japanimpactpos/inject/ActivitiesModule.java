package ch.japan_impact.japanimpactpos.inject;

import ch.japan_impact.japanimpactpos.views.ConfigurationPickerActivity;
import ch.japan_impact.japanimpactpos.views.LoginActivity;
import ch.japan_impact.japanimpactpos.views.pos.CashPaymentActivity;
import ch.japan_impact.japanimpactpos.views.pos.OrderSummaryActivity;
import ch.japan_impact.japanimpactpos.views.pos.POSActivity;
import ch.japan_impact.japanimpactpos.views.scan.ScanActivity;
import dagger.Module;
import dagger.android.AndroidInjectionModule;
import dagger.android.ContributesAndroidInjector;

/**
 * @author Louis Vialar
 */
@Module(includes = {
        AndroidInjectionModule.class
})
abstract class ActivitiesModule {
    @ContributesAndroidInjector
    abstract LoginActivity loginActivity();
    @ContributesAndroidInjector
    abstract ConfigurationPickerActivity pickerActivity();
    @ContributesAndroidInjector
    abstract POSActivity posActivity();
    @ContributesAndroidInjector
    abstract CashPaymentActivity cashPaymentActivity();
    @ContributesAndroidInjector
    abstract OrderSummaryActivity orderSummaryActivity();
    @ContributesAndroidInjector
    abstract ScanActivity scanActivity();
}
