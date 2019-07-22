package com.rocsoftware.mymirror;
/**
 * Created by Rogelio on 27/11/2016.
 */


import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.support.design.widget.BottomSheetDialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.widget.RelativeLayout;


public class   AboutDialogFragment extends DialogFragment {
    //DialogFragment
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction

        LayoutInflater inflater = getActivity().getLayoutInflater();
        RelativeLayout aboutLayout = (RelativeLayout) inflater.inflate(R.layout.about_layout, null);


        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(aboutLayout);
        // Create the AlertDialog object and return it
        return builder.create();
    }
}