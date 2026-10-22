package sovereign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import indevo.abilities.splitfleet.FleetUtils;
import indevo.abilities.splitfleet.fleetManagement.Behaviour;
import indevo.abilities.splitfleet.fleetManagement.Behaviour.FleetBehaviour;
import indevo.abilities.splitfleet.fleetManagement.DetachmentMemory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Industrial.Evolution detachments. Only touch this class after SovereignState.indevo() says the mod is on,
 * since every method here needs its classes.
 */
final class IndEvoLink {

    static final class Detachment {
        final int number;
        final CampaignFleetAPI fleet;

        Detachment(int number, CampaignFleetAPI fleet) {
            this.number = number;
            this.fleet = fleet;
        }
    }

    private IndEvoLink() {
    }

    static List<Detachment> detachments() {
        List<Detachment> out = new ArrayList<Detachment>();
        try {
            Map<Integer, CampaignFleetAPI> map = DetachmentMemory.getFleetMap();
            List<Integer> numbers = new ArrayList<Integer>(map.keySet());
            Collections.sort(numbers);
            for (Integer n : numbers) {
                CampaignFleetAPI fleet = map.get(n);
                if (fleet != null) {
                    out.add(new Detachment(n, fleet));
                }
            }
        } catch (Exception ex) {
            Global.getLogger(IndEvoLink.class).warn("Could not read detachments", ex);
        }
        return out;
    }

    static boolean dormant(CampaignFleetAPI fleet) {
        return Behaviour.isDormant(fleet);
    }

    static String status(CampaignFleetAPI fleet) {
        if (Behaviour.isDormant(fleet)) {
            return "With your fleet";
        }
        if (Behaviour.isReturning(fleet)) {
            return "Returning";
        }
        FleetBehaviour b = Behaviour.getFleetBehaviour(fleet, false);
        if (b == null) {
            return "Idle";
        }
        switch (b) {
            case STAY:
                return "Holding";
            case FOLLOW:
                return "Following you";
            case HIDE:
                return "Hiding";
            case ESCORT:
                return "Escorting";
            case INTERCEPT:
                return "Intercepting";
            case TRANSPORT:
                return "Transporting";
            case DELIVER:
                return "Delivering";
            case COMBAT:
                return "In combat";
            case RETURN_TO_PLAYER_AND_MERGE:
                return "Returning";
            default:
                return "Idle";
        }
    }

    /** @param order hold, follow, hide or recall */
    static SovereignActions.Outcome order(CampaignFleetAPI fleet, String order) {
        try {
            if (Behaviour.isDormant(fleet)) {
                return new SovereignActions.Outcome(false, "It is still part of your fleet.");
            }
            FleetBehaviour b;
            String phrase;
            if ("hold".equals(order)) {
                b = FleetBehaviour.STAY;
                phrase = "holds position";
            } else if ("follow".equals(order)) {
                b = FleetBehaviour.FOLLOW;
                phrase = "follows you";
            } else if ("hide".equals(order)) {
                b = FleetBehaviour.HIDE;
                phrase = "goes dark";
            } else {
                b = FleetBehaviour.RETURN_TO_PLAYER_AND_MERGE;
                phrase = "returns to merge with you";
            }
            Behaviour.changeBehaviourAndUpdateAI(fleet, b);
            String text = fleet.getName() + " " + phrase + ".";
            SovereignLog.add(SovereignLog.ORDER, text);
            return new SovereignActions.Outcome(true, text);
        } catch (Exception ex) {
            return SovereignActions.failed("Detachment order failed", ex);
        }
    }

    static boolean detachmentFleet(CampaignFleetAPI fleet) {
        return FleetUtils.isDetachmentFleet(fleet);
    }
}
