package ch.japan_impact.japanimpactpos.views.pos;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import ch.japan_impact.japanimpactpos.R;
import dagger.android.AndroidInjection;

/**
 * @author Louis Vialar
 */
public class CashPaymentActivity extends AppCompatActivity {
    public static final String PRICE_TO_PAY = "PRICE_TO_PAY";
    public static final String IS_SUCCESSFUL_PAYMENT = "CASH_PAYMENT_SUCCESS";

    private int price;

    private void returnToMain(boolean success) {
        Intent result = new Intent();
        result.putExtra(IS_SUCCESSFUL_PAYMENT, success);
        setResult(Activity.RESULT_OK, result);
        finish();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AndroidInjection.inject(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cash_payment);

        Intent intent = getIntent();
        this.price = intent.getIntExtra(PRICE_TO_PAY, -1);

        if (price < 0) {
            Toast.makeText(this, "Prix invalide", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        TextView change = findViewById(R.id.change_to_return);
        setTitle(getString(R.string.cash_payment_title, price));

        EditText cashGiven = findViewById(R.id.cash_given);
        cashGiven.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    int changeGiven = s.toString().isEmpty() ? 0 : Integer.parseInt(s.toString());
                    int diff = changeGiven - price;

                    if (diff < 0)
                        change.setText("Pas encore assez d'argent");
                    else change.setText("Rendre " + diff + " .-");
                } catch (NumberFormatException e) {
                    Toast.makeText(CashPaymentActivity.this, "Valeur entrée incorrecte", Toast.LENGTH_SHORT).show();
                }
            }
        });

        Button cancel = findViewById(R.id.cash_payment_abort);
        cancel.setOnClickListener(v -> returnToMain(false));

        Button accept = findViewById(R.id.cash_payment_accept);
        accept.setOnClickListener(v -> returnToMain(true));


        findViewById(R.id.btn_add_200).setOnClickListener(new AddMoneyListener(200, cashGiven));
        findViewById(R.id.btn_add_100).setOnClickListener(new AddMoneyListener(100, cashGiven));
        findViewById(R.id.btn_add_50).setOnClickListener(new AddMoneyListener(50, cashGiven));
        findViewById(R.id.btn_add_20).setOnClickListener(new AddMoneyListener(20, cashGiven));
        findViewById(R.id.btn_add_10).setOnClickListener(new AddMoneyListener(10, cashGiven));

    }

    @Override
    public void onBackPressed() {
        returnToMain(false);
    }

    static class AddMoneyListener implements View.OnClickListener {
        private final int howMuch;
        private final EditText field;

        AddMoneyListener(int howMuch, EditText field) {
            this.howMuch = howMuch;
            this.field = field;
        }

        @Override
        public void onClick(View v) {
            int nv = howMuch;
            try {
                nv = Integer.parseInt(field.getText().toString()) + howMuch;
            } catch (NumberFormatException ignored) {}

            field.setText("" + nv);
        }
    }
}
