package ch.japan_impact.japanimpactpos.views.pos;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.recyclerview.widget.RecyclerView;
import ch.japan_impact.japanimpactpos.R;
import ch.japan_impact.japanimpactpos.data.pos.CheckedOutItem;
import ch.japan_impact.japanimpactpos.data.pos.JIItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Louis Vialar
 */
public class Cart {
    private final List<CartedItem> content = new ArrayList<>();
    private final CartedItemAdapter adapter = new CartedItemAdapter();

    private final POSActivity activity;
    private final MutableLiveData<Integer> totalPrice = new MutableLiveData<>();

    private boolean enabled = true;
    private boolean changed = false;
    private int orderId = -1;
    private int serverPrice = -1;

    Cart(POSActivity activity) {
        this.activity = activity;
        this.totalPrice.setValue(0);
    }

    public void clear() {
        synchronized (content) {
            this.content.clear();
            this.orderId = -1;
            this.serverPrice = -1;
            this.changed = false;
            this.totalPrice.setValue(0);

            adapter.notifyDataSetChanged();
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (this.enabled) {
            this.orderId = -1;
            this.serverPrice = -1;
        }
    }

    public LiveData<Integer> getPrice() {
        return this.totalPrice;
    }

    public CartedItemAdapter getAdapter() {
        return adapter;
    }

    public List<CartedItem> getContent() {
        return Collections.unmodifiableList(content);
    }

    public List<CheckedOutItem> getOrder() {
        return content.stream()
                .map(CartedItem::toCheckedOutItem)
                .filter(item -> item.getItemAmount() > 0)
                .collect(Collectors.toList());
    }

    public void addItem(JIItem item) {
        synchronized (content) {
            totalPrice.setValue(totalPrice.getValue() + item.getPrice());
            changed = true;

            for (CartedItem c : content) {
                if (c.item.getId() == item.getId()) {
                    c.add(1);
                    return;
                }
            }

            content.add(new CartedItem(item, 1));
            adapter.notifyDataSetChanged();
        }

    }

    public void removeItem(JIItem item) {
        synchronized (content) {
            totalPrice.setValue(totalPrice.getValue() - item.getPrice());

            Iterator<CartedItem> iter = content.iterator();
            CartedItem c;

            while ((c = iter.next()) != null) {
                if (c.item.getId() == item.getId()) {
                    boolean delete = c.remove(1);
                    if (delete) {
                        c.watchableQuantity.removeObservers(activity);
                        iter.remove();
                        adapter.notifyDataSetChanged();
                    }
                    changed = true;
                    return;
                }
            }
        }
    }

    public int getOrderId() {
        return orderId;
    }

    public int getServerPrice() {
        return serverPrice;
    }

    public void setServerResponse(int orderId, int serverPrice) {
        this.orderId = orderId;
        this.serverPrice = serverPrice;
    }

    public boolean isChanged() {
        return changed;
    }

    public void resetChangeCounter() {
        this.changed = false;
    }

    static class CartedItem implements Parcelable {
        final @NonNull JIItem item;
        int quantity;
        final MutableLiveData<Integer> watchableQuantity = new MutableLiveData<>();

        CartedItem(JIItem item, int quantity) {
            this.item = item;
            this.quantity = quantity;
            this.watchableQuantity.postValue(quantity);
        }

        protected CartedItem(Parcel in) {
            item = in.readParcelable(JIItem.class.getClassLoader());
            quantity = in.readInt();
        }

        public static final Creator<CartedItem> CREATOR = new Creator<CartedItem>() {
            @Override
            public CartedItem createFromParcel(Parcel in) {
                return new CartedItem(in);
            }

            @Override
            public CartedItem[] newArray(int size) {
                return new CartedItem[size];
            }
        };

        void add(int howMany) {
            this.quantity += howMany;
            this.watchableQuantity.postValue(this.quantity);
        }

        boolean remove(int howMany) {
            add(-howMany);
            return quantity <= 0;
        }

        CheckedOutItem toCheckedOutItem() {
            return new CheckedOutItem(item.getId(), quantity, item.getPrice());
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeParcelable(item, flags);
            dest.writeInt(quantity);
        }
    }


    class CartedItemAdapter extends RecyclerView.Adapter<CartedItemViewHolder> {
        @NonNull
        @Override
        public CartedItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            LayoutInflater layoutInflater = LayoutInflater.from(context);
            View view = layoutInflater.inflate(R.layout.cart_line, parent, false);

            return new CartedItemViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CartedItemViewHolder holder, int position) {
            Log.i("WTF", "Binding item " + position + " " + content.get(position) + " to holder " + holder);
            holder.setItem(content.get(position));
        }

        @Override
        public int getItemCount() {
            return content.size();
        }
    }

    class CartedItemViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private CartedItem item;
        private TextView itemName;
        private TextView itemPrice;
        private TextView itemQuantity;

        public CartedItemViewHolder(@NonNull View itemView) {
            super(itemView);

            itemView.setOnClickListener(this);
            itemName = itemView.findViewById(R.id.carted_item_name);
            itemPrice = itemView.findViewById(R.id.carted_item_price);
            itemQuantity = itemView.findViewById(R.id.carted_item_quantity);

            itemView.setVisibility(View.VISIBLE);
        }

        @Override
        public void onClick(View v) {
            if (this.item != null) {
                if (!enabled) {
                    Toast.makeText(activity, "Le composant est désactivé !", Toast.LENGTH_SHORT).show();
                    return;
                }

                removeItem(this.item.item);
                Toast.makeText(activity, "Retiré: 1x " + item.item.getName(), Toast.LENGTH_SHORT).show();
            }
        }

        @SuppressLint("SetTextI18n")
        void setItem(@Nullable CartedItem item) {
            this.item = item;

            if (item != null) {
                this.itemView.setBackgroundResource(R.drawable.itemborder);
                itemName.setText(item.item.getName());
                item.watchableQuantity.observe(activity, integer -> {
                    itemQuantity.setText("x" + integer);
                    itemPrice.setText("Soit " + item.item.getPrice() * integer + " .-");
                });
            }
        }
    }
}
